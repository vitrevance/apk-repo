package com.apkrepo.backend.ingestion

import com.apkrepo.backend.api.dto.AdminReleaseDto
import com.apkrepo.backend.api.dto.AppDetailDto
import com.apkrepo.backend.api.dto.ReleaseUploadResultDto
import com.apkrepo.backend.api.dto.UpdateAppRequestDto
import com.apkrepo.backend.api.dto.toAdminDto
import com.apkrepo.backend.api.dto.toDetailDto
import com.apkrepo.backend.api.error.ApkValidationException
import com.apkrepo.backend.api.error.BadRequestException
import com.apkrepo.backend.api.error.ConflictCode
import com.apkrepo.backend.api.error.ConflictException
import com.apkrepo.backend.api.error.NotFoundException
import com.apkrepo.backend.config.ApkRepoProperties
import com.apkrepo.backend.domain.App
import com.apkrepo.backend.domain.Release
import com.apkrepo.backend.domain.ReleaseStatus
import com.apkrepo.backend.publisher.PublicationJobEnqueuer
import com.apkrepo.backend.repository.AppRepository
import com.apkrepo.backend.repository.ReleaseRepository
import com.apkrepo.backend.storage.StorageService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Orchestrates the upload pipeline (plan §5 steps 1–7): staging, structural
 * validation, signature + metadata analysis, identity checks, immutable
 * storage and persistence. Analysis is synchronous; a successfully ingested
 * release ends in status READY and a PUBLISH job is enqueued for the F-Droid
 * publisher worker.
 */
@Service
class ApkIngestionService(
    private val storageService: StorageService,
    private val apkValidator: ApkValidator,
    private val apkAnalyzer: ApkAnalyzer,
    private val appRepository: AppRepository,
    private val releaseRepository: ReleaseRepository,
    private val jobEnqueuer: PublicationJobEnqueuer,
    private val properties: ApkRepoProperties,
    transactionManager: PlatformTransactionManager,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    fun upload(file: MultipartFile): ReleaseUploadResultDto {
        val originalFilename = file.originalFilename ?: ""
        if (!originalFilename.endsWith(".apk", ignoreCase = true)) {
            throw ApkValidationException(
                ApkErrorCode.NOT_AN_APK,
                "Only .apk files are accepted (uploaded: \"$originalFilename\").",
            )
        }

        val staged = storageService.createStagingFile()
        try {
            streamToStaging(file, staged)
            val sha256 = apkValidator.validateAndHash(staged)

            // Idempotency: a byte-identical upload returns the existing release.
            val existing = inTransaction {
                releaseRepository.findByApkSha256(sha256).map { it.toAdminDto() }.orElse(null)
            }
            if (existing != null) {
                return ReleaseUploadResultDto(
                    duplicate = true,
                    isNewApp = false,
                    release = existing,
                )
            }

            val metadata = apkAnalyzer.analyze(staged)

            // Identity rules (plan §4 constraints).
            inTransaction {
                val app = appRepository.findByPackageName(metadata.packageName).orElse(null)
                if (app != null) {
                    releaseRepository.findByAppIdAndVersionCode(app.id, metadata.versionCode)
                        .ifPresent {
                            throw ConflictException(
                                ConflictCode.DUPLICATE_VERSION,
                                "Version ${metadata.versionCode} of ${metadata.packageName} " +
                                    "already exists with different content.",
                            )
                        }
                    val signerMismatch = releaseRepository.findByAppId(app.id).any {
                        it.apkSignerSha256 != null && it.apkSignerSha256 != metadata.signerSha256
                    }
                    if (signerMismatch) {
                        throw ConflictException(
                            ConflictCode.SIGNER_MISMATCH,
                            "APK signer certificate differs from earlier releases of " +
                                "${metadata.packageName}; Android would reject such updates.",
                        )
                    }
                }
            }

            // Promote to immutable storage (content-addressed) and store the icon.
            val storedPath = storageService.promoteApkToStore(staged, sha256)
            val apkSize = Files.size(storedPath)
            val iconFileName = metadata.icon
                ?.let { storageService.storeIcon(sha256, it.data, it.extension) }

            return inTransaction {
                var app = appRepository.findByPackageName(metadata.packageName).orElse(null)
                val isNewApp = app == null
                if (app == null) {
                    app = appRepository.save(
                        App(
                            packageName = metadata.packageName,
                            displayName = metadata.label ?: metadata.packageName,
                        ),
                    )
                }
                // The icon follows the latest uploaded release; admin edits to
                // display name/description are never overwritten.
                if (iconFileName != null && app.iconPath != iconFileName) {
                    app.iconPath = iconFileName
                    appRepository.save(app)
                }

                val release = releaseRepository.save(
                    Release(
                        app = app,
                        versionName = metadata.versionName,
                        versionCode = metadata.versionCode,
                        apkSha256 = sha256,
                        apkSignerSha256 = metadata.signerSha256,
                        apkPath = storageService.apkRelativePath(sha256),
                        apkSize = apkSize,
                        minSdk = metadata.minSdk,
                        targetSdk = metadata.targetSdk,
                        status = ReleaseStatus.READY,
                    ),
                )
                // Hand the new release to the F-Droid publisher (joined
                // transaction: job and release commit atomically).
                jobEnqueuer.enqueuePublish(release.id)
                ReleaseUploadResultDto(
                    duplicate = false,
                    isNewApp = isNewApp,
                    release = release.toAdminDto(),
                )
            }!!
        } finally {
            storageService.deleteStagingFile(staged)
        }
    }

    @Transactional(readOnly = true)
    fun getRelease(id: UUID): AdminReleaseDto =
        releaseRepository.findById(id)
            .map { it.toAdminDto() }
            .orElseThrow { NotFoundException("Release not found: $id") }

    @Transactional(readOnly = true)
    fun listReleases(page: Int, size: Int, status: ReleaseStatus?): Page<AdminReleaseDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"))
        val releases = if (status == null) {
            releaseRepository.findAll(pageable)
        } else {
            releaseRepository.findByStatus(status, pageable)
        }
        return releases.map { it.toAdminDto() }
    }

    /**
     * Marks a release unpublished. The APK file is retained; an UNPUBLISH
     * job removes it from the F-Droid repository on the next regeneration.
     */
    @Transactional
    fun unpublishRelease(id: UUID) {
        val release = releaseRepository.findById(id)
            .orElseThrow { NotFoundException("Release not found: $id") }
        release.status = ReleaseStatus.UNPUBLISHED
        release.publishedAt = null
        releaseRepository.save(release)
        jobEnqueuer.enqueueUnpublish(id)
    }

    /** Edits catalog metadata without touching the app's APK identity (plan scenario 6). */
    @Transactional
    fun updateAppMetadata(packageName: String, request: UpdateAppRequestDto): AppDetailDto {
        val app = appRepository.findByPackageName(packageName)
            .orElseThrow { NotFoundException("App not found: $packageName") }
        if (request.displayName == null && request.description == null) {
            throw BadRequestException("At least one of displayName or description must be provided.")
        }
        request.displayName?.let { app.displayName = it }
        request.description?.let { app.description = it }
        appRepository.save(app)

        // The metadata yml in the F-Droid repository is generated from these
        // fields, so a rebuild job is needed to publish the edit.
        jobEnqueuer.enqueueRebuild()

        // Same visibility rule as the public catalog (READY or PUBLISHED).
        val latest = releaseRepository
            .findFirstByAppIdAndStatusInOrderByVersionCodeDesc(
                app.id,
                listOf(ReleaseStatus.READY, ReleaseStatus.PUBLISHED),
            )
        return app.toDetailDto(latest)
    }

    /** Everything the download endpoint needs, resolved inside a transaction. */
    @Transactional(readOnly = true)
    fun downloadInfo(releaseId: UUID): ReleaseDownloadInfo {
        val release = releaseRepository.findById(releaseId)
            .orElseThrow { NotFoundException("Release not found: $releaseId") }
        if (release.status != ReleaseStatus.READY && release.status != ReleaseStatus.PUBLISHED) {
            throw NotFoundException("Release $releaseId is not available for download.")
        }
        return ReleaseDownloadInfo(
            apkPath = release.apkPath,
            apkSize = release.apkSize,
            apkSha256 = release.apkSha256,
            fileName = "${release.app.packageName}_${release.versionName}.apk",
        )
    }

    private fun streamToStaging(file: MultipartFile, staged: Path) {
        val maxBytes = properties.upload.maxFileSizeBytes
        var total = 0L
        file.inputStream.use { input ->
            Files.newOutputStream(staged, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) {
                        throw ApkValidationException(
                            ApkErrorCode.SIZE_EXCEEDED,
                            "Uploaded file exceeds the ${maxBytes / (1024 * 1024)} MB limit.",
                        )
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    /**
     * Runs [block] in a short transaction; analysis/file IO stays outside
     * transactions. Returns null when [block] itself returns null (e.g. an
     * orElse(null) lookup).
     */
    private fun <T> inTransaction(block: () -> T): T? = transactionTemplate.execute { block() }
}

data class ReleaseDownloadInfo(
    val apkPath: String,
    val apkSize: Long,
    val apkSha256: String,
    val fileName: String,
)
