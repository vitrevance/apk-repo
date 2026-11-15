package com.apkrepo.backend.publisher

import com.apkrepo.backend.config.ApkRepoProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolved locations of the private fdroidserver workspace inside the
 * storage root. Everything except [repoDir] is private and never served:
 * config.yml contains the keystore passwords, keystore.jks is the repo
 * signing key, metadata holds the generated per-app yml files.
 *
 * Layout:
 * - baseDir   {root}/fdroid          private workspace
 * - repoDir   {root}/fdroid/repo     public output, served at /fdroid/repo
 * - configPath, keystorePath, metadataDir inside baseDir
 */
@Component
class FdroidWorkspace(properties: ApkRepoProperties) {

    private val storageRoot: Path = Path.of(properties.storage.root).toAbsolutePath().normalize()

    /** Private workspace root (config.yml, keystore.jks, metadata). */
    val baseDir: Path = storageRoot.resolve("fdroid")

    /** Public repository output served over HTTP. */
    val repoDir: Path = baseDir.resolve("repo")

    val configPath: Path = baseDir.resolve("config.yml")

    /**
     * The repository signing keystore. fdroidserver's default keystore
     * name is keystore.p12 (PKCS12), and that is what fdroid init
     * creates; keystore.jks is also accepted for pre-provisioned keys.
     */
    val keystorePath: Path = baseDir.resolve("keystore.p12")

    val keystoreJksPath: Path = baseDir.resolve("keystore.jks")

    val metadataDir: Path = baseDir.resolve("metadata")

    fun ensureDirectories() {
        Files.createDirectories(baseDir)
        Files.createDirectories(repoDir)
        Files.createDirectories(metadataDir)
    }

    /** True once fdroid init has created the repository signing keystore. */
    fun isInitialized(): Boolean =
        Files.exists(keystorePath) || Files.exists(keystoreJksPath)

    /** The existing keystore file, whichever naming fdroid init produced. */
    fun existingKeystore(): Path? =
        if (Files.exists(keystorePath)) keystorePath
        else if (Files.exists(keystoreJksPath)) keystoreJksPath
        else null
}
