package com.apkrepo.backend.publisher

import com.apkrepo.backend.storage.StorageService
import org.springframework.stereotype.Component
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Mirrors the database release state into the fdroid repo directory.
 * APKs are hardlinked from the content-addressed store (same volume, so
 * the repo costs almost no extra space) with a copy fallback; every repo
 * file that does not correspond to a publishable release is removed so
 * unpublishes disappear from the repository on the next regeneration.
 * Index files owned by fdroidserver (entry.jar, index-v1.jar,
 * index-v2.json, icons, QR code) are never touched here.
 *
 * The class is deliberately free of database access: callers collect the
 * publishable releases (status READY or PUBLISHED) in a transaction and
 * pass them in, which keeps the sync logic unit-testable.
 */
@Component
class RepoFileSyncer(
    private val workspace: FdroidWorkspace,
    private val storageService: StorageService,
) {

    /** One publishable release, collected from the database by the caller. */
    data class SyncEntry(
        val releaseId: UUID,
        val packageName: String,
        val versionCode: Long,
        val apkSha256: String,
    )

    data class SyncReport(
        val publishableReleaseIds: List<UUID>,
        val publishablePackageNames: Set<String>,
        val removedRepoFiles: List<String>,
        val removedMetadataFiles: List<String>,
    )

    /**
     * Pluggable hardlink step (test seam: set to a failing function to
     * exercise the copy fallback).
     */
    internal var linkStrategy: (source: Path, target: Path) -> Boolean = ::createHardlink

    fun sync(entries: List<SyncEntry>): SyncReport {
        workspace.ensureDirectories()
        val expectedFiles = entries.map { repoFileName(it) }.toSet()

        // Remove repo APKs that no longer correspond to a publishable release.
        val removedRepoFiles = mutableListOf<String>()
        listDir(workspace.repoDir, "*.apk").forEach { path ->
            val name = path.fileName.toString()
            if (name !in expectedFiles) {
                Files.deleteIfExists(path)
                removedRepoFiles += name
            }
        }

        // Link (or copy) every publishable release into the repo.
        entries.forEach { entry ->
            val target = workspace.repoDir.resolve(repoFileName(entry))
            if (!Files.exists(target)) {
                val source = storageService.apkAbsolutePath(entry.apkSha256)
                if (!Files.exists(source)) {
                    throw PublisherException("APK ${entry.apkSha256} is missing from the store at $source")
                }
                if (!linkStrategy(source, target)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        // Remove metadata yml files for packages without publishable releases.
        val publishablePackages = entries.map { it.packageName }.toSet()
        val removedMetadataFiles = mutableListOf<String>()
        listDir(workspace.metadataDir, "*.yml").forEach { path ->
            val pkg = path.fileName.toString().removeSuffix(".yml")
            if (pkg !in publishablePackages) {
                Files.deleteIfExists(path)
                removedMetadataFiles += path.fileName.toString()
            }
        }

        return SyncReport(
            publishableReleaseIds = entries.map { it.releaseId },
            publishablePackageNames = publishablePackages,
            removedRepoFiles = removedRepoFiles,
            removedMetadataFiles = removedMetadataFiles,
        )
    }

    /** fdroidserver's repo file naming convention: <package>_<versionCode>.apk */
    private fun repoFileName(entry: SyncEntry): String =
        "${entry.packageName}_${entry.versionCode}.apk"

    private fun listDir(dir: Path, glob: String): List<Path> {
        if (!Files.isDirectory(dir)) return emptyList()
        Files.newDirectoryStream(dir, glob).use { stream -> return stream.toList() }
    }

    private fun createHardlink(source: Path, target: Path): Boolean =
        try {
            Files.createLink(target, source)
            true
        } catch (_: UnsupportedOperationException) {
            false
        } catch (_: FileSystemException) {
            false
        }
}
