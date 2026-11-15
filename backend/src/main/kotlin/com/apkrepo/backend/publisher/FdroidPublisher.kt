package com.apkrepo.backend.publisher

import com.apkrepo.backend.config.ApkRepoProperties
import com.apkrepo.backend.domain.Release
import com.apkrepo.backend.domain.ReleaseStatus
import com.apkrepo.backend.repository.AppRepository
import com.apkrepo.backend.repository.ReleaseRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarFile

/**
 * The F-Droid publication pipeline (plan section 6). Every job performs a
 * full repository regeneration, which keeps the pipeline idempotent and
 * makes PUBLISH / UNPUBLISH / REBUILD jobs differ only in audit semantics:
 *
 * 1. ensureInitialized — fdroid init on first run creates keystore.jks and
 *    config.yml; the config is then (re)patched with our repo identity.
 * 2. RepoFileSyncer.sync — mirror the DB release state into the repo dir.
 * 3. MetadataYmlWriter.writeAll — regenerate metadata yml from the DB.
 * 4. fdroid update — parse APKs, extract icons, sign entry.jar/index-v1.jar
 *    and write index-v2.json + the QR code.
 * 5. Post-verification — entry.jar is signed, index-v2.json parses as JSON.
 *
 * All mutating methods are synchronized: the publisher is the single
 * writer of the workspace, matching the serial job worker.
 */
@Component
class FdroidPublisher(
    private val workspace: FdroidWorkspace,
    private val cli: FdroidCli,
    private val syncer: RepoFileSyncer,
    private val metadataWriter: MetadataYmlWriter,
    private val appRepository: AppRepository,
    private val releaseRepository: ReleaseRepository,
    private val properties: ApkRepoProperties,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(FdroidPublisher::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val objectMapper = ObjectMapper()
    private val fingerprintCache = AtomicReference<String?>()

    /**
     * First-run setup: run fdroid init (creates keystore.jks + config.yml)
     * and keep config.yml in sync with our repository identity. Idempotent.
     */
    @Synchronized
    fun ensureInitialized() {
        workspace.ensureDirectories()

        if (!workspace.isInitialized()) {
            log.info("Initializing F-Droid repository workspace at {}", workspace.baseDir)
            val result = cli.run(
                workspace.baseDir,
                "init",
                "--no-prompt",
                "--keystore",
                workspace.keystorePath.toString(),
            )
            if (result.exitCode != 0 || !workspace.isInitialized()) {
                throw PublisherException(
                    "fdroid init failed (exit ${result.exitCode})",
                    result.output,
                )
            }
            log.info("F-Droid repository signing key created at {}", workspace.keystorePath)
        }

        patchConfig()
    }

    /**
     * Runs one full repository regeneration for the given job and returns
     * what the run produced. Releases are not flipped to PUBLISHED here —
     * the worker does that transactionally once this returns successfully.
     */
    @Synchronized
    fun publishOnce(job: com.apkrepo.backend.domain.PublicationJob): PublicationOutcome {
        ensureInitialized()

        // 1. Collect publishable releases in a short transaction.
        val entries = collectPublishableEntries()

        // 2. Mirror them into the repo directory.
        val report = syncer.sync(entries)

        // 3. Regenerate metadata from the database.
        val apps = transactionTemplate.execute {
            appRepository.findAll().filter { it.packageName in report.publishablePackageNames }
        }.orEmpty()
        metadataWriter.writeAll(apps)

        // 4. Regenerate and sign the indexes.
        val update = cli.run(workspace.baseDir, "update")
        if (update.exitCode != 0) {
            throw PublisherException("fdroid update failed (exit ${update.exitCode})", update.output)
        }

        // 5. Cheap in-pipeline verification of what fdroid update produced.
        postVerify()

        val fingerprint = extractFingerprint()
        fingerprintCache.set(fingerprint)
        return PublicationOutcome(
            generationId = job.generationId,
            publishedReleaseIds = report.publishableReleaseIds,
            fingerprint = fingerprint,
        )
    }

    /**
     * SHA-256 of the entry.jar signing certificate, lowercase hex without
     * separators — the format used in the fingerprint URL parameter.
     * Null until the repository has been generated at least once.
     */
    fun extractFingerprint(): String? {
        val entryJar = workspace.repoDir.resolve("entry.jar")
        if (!Files.exists(entryJar)) return null
        try {
            JarFile(entryJar.toFile(), true).use { jar ->
                val entry = jar.getJarEntry("entry.json") ?: return null
                // Reading the entry fully verifies the signature and
                // attaches the signer certificates to the JarEntry.
                jar.getInputStream(entry).use { it.readBytes() }
                val certificate = entry.certificates?.firstOrNull() ?: return null
                val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            }
        } catch (e: Exception) {
            log.warn("Could not extract fingerprint from {}", entryJar, e)
            return null
        }
    }

    /**
     * The live repository fingerprint: cached from the last successful
     * publish, lazily read from disk on first call after startup.
     */
    fun currentFingerprint(): String? =
        fingerprintCache.get() ?: extractFingerprint()?.also { fingerprintCache.set(it) }

    private fun collectPublishableEntries(): List<RepoFileSyncer.SyncEntry> =
        transactionTemplate.execute {
            releaseRepository.findAll()
                .filter { it.status == ReleaseStatus.READY || it.status == ReleaseStatus.PUBLISHED }
                .map { it.toSyncEntry() }
        }.orEmpty()

    private fun Release.toSyncEntry(): RepoFileSyncer.SyncEntry = RepoFileSyncer.SyncEntry(
        releaseId = id,
        packageName = app.packageName,
        versionCode = versionCode,
        apkSha256 = apkSha256,
    )

    private fun postVerify() {
        val entryJar = workspace.repoDir.resolve("entry.jar")
        if (!Files.exists(entryJar)) {
            throw PublisherException("fdroid update did not produce entry.jar")
        }
        val indexV2 = workspace.repoDir.resolve("index-v2.json")
        if (!Files.exists(indexV2)) {
            throw PublisherException("fdroid update did not produce index-v2.json")
        }
        if (extractFingerprint() == null) {
            throw PublisherException("entry.jar is not signed or its signer is unreadable")
        }
        try {
            val index: JsonNode = objectMapper.readTree(indexV2.toFile())
            if (index.get("repo")?.get("timestamp") == null) {
                throw PublisherException("index-v2.json has no repo.timestamp")
            }
        } catch (e: PublisherException) {
            throw e
        } catch (e: Exception) {
            throw PublisherException("index-v2.json is not valid JSON: ${e.message}")
        }
    }

    /**
     * Line-based config.yml patch that keeps fdroidserver's own lines
     * (keystore passwords, key alias, key dname) untouched and (re)applies
     * our repository identity. Mirrors fdroidserver's own write_to_config
     * behaviour: an existing line — commented or not — is replaced with the
     * active value; a missing key is appended.
     */
    private fun patchConfig() {
        val config = workspace.configPath
        if (!Files.exists(config)) {
            throw PublisherException("config.yml not found at $config after fdroid init")
        }
        val repoUrl = properties.repository.baseUrl.trimEnd('/') + "/fdroid/repo"
        // fdroid init writes the --keystore value into config.yml but
        // generates the key file under its own default name (keystore.p12);
        // normalize the config to the file that actually exists.
        val keystore = workspace.existingKeystore()?.toString()
        val values = linkedMapOf(
            "repo_url" to repoUrl,
            "repo_name" to properties.repository.name,
            "repo_description" to "APK Repo - self-hosted application repository",
            "archive_older" to "0",
        )
        if (keystore != null) {
            values["keystore"] = keystore
        }

        val lines = Files.readAllLines(config).toMutableList()
        values.forEach { (key, value) ->
            val pattern = Regex("^[\\s#]*$key:.*")
            val replacement = yamlLine(key, value)
            val index = lines.indexOfFirst { pattern.matches(it) }
            if (index >= 0) {
                lines[index] = replacement
            } else {
                lines += replacement
            }
        }
        Files.write(config, lines)
    }

    private fun yamlLine(key: String, value: String): String {
        val needsQuoting = value.contains(':') || value.contains('#') || value != value.trim()
        return if (needsQuoting) "$key: \"$value\"" else "$key: $value"
    }
}
