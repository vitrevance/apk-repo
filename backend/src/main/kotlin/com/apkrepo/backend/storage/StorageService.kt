package com.apkrepo.backend.storage

import com.apkrepo.backend.config.ApkRepoProperties
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Filesystem storage for APKs and icons.
 *
 * Layout under the configured storage root:
 * - staging/&lt;uuid&gt;.part — untrusted upload staging with random names
 * - apks/&lt;sha256&gt;.apk  — immutable, content-addressed APK store
 * - icons/&lt;sha256&gt;.png|webp|jpg — extracted icons, content-addressed
 *
 * Stored APK and icon files are addressed by the SHA-256 of the APK bytes,
 * so names never change and duplicate content deduplicates naturally.
 */
@Service
class StorageService(properties: ApkRepoProperties) {

    private val root: Path = Path.of(properties.storage.root).toAbsolutePath().normalize()
    private val stagingDir: Path = root.resolve("staging")
    private val apkDir: Path = root.resolve("apks")
    private val iconDir: Path = root.resolve("icons")

    init {
        Files.createDirectories(stagingDir)
        Files.createDirectories(apkDir)
        Files.createDirectories(iconDir)
    }

    /** Creates a new staging file with a random name for an untrusted upload. */
    fun createStagingFile(): Path {
        Files.createDirectories(stagingDir)
        return Files.createTempFile(stagingDir, "upload-", ".part")
    }

    fun deleteStagingFile(path: Path) {
        Files.deleteIfExists(path)
    }

    /**
     * Atomically promotes a validated staging file into the immutable APK
     * store. If the target already exists (byte-identical content was stored
     * before) the staging file is removed and the existing path returned.
     */
    fun promoteApkToStore(stagedFile: Path, sha256: String): Path {
        Files.createDirectories(apkDir)
        val target = apkDir.resolve("$sha256.apk")
        if (Files.exists(target)) {
            Files.deleteIfExists(stagedFile)
            return target
        }
        try {
            Files.move(stagedFile, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(stagedFile, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    /** Storage-relative path of an APK, persisted in releases.apk_path. */
    fun apkRelativePath(sha256: String): String = "apks/$sha256.apk"

    /** Absolute path of a stored APK (same volume as the fdroid workspace). */
    fun apkAbsolutePath(sha256: String): Path = apkDir.resolve("$sha256.apk")

    fun apkExists(sha256: String): Boolean = Files.exists(apkDir.resolve("$sha256.apk"))

    fun apkSize(apkRelativePath: String): Long {
        val path = resolveWithinRoot(apkRelativePath)
        return Files.size(path)
    }

    fun openApkStream(apkRelativePath: String): InputStream {
        val path = resolveWithinRoot(apkRelativePath)
        return Files.newInputStream(path)
    }

    /** Stores an extracted icon; returns the icon file name to persist on the app. */
    fun storeIcon(apkSha256: String, data: ByteArray, extension: String): String {
        Files.createDirectories(iconDir)
        val name = "$apkSha256.$extension"
        val target = iconDir.resolve(name)
        if (!Files.exists(target)) {
            Files.write(target, data)
        }
        return name
    }

    /** Reads a stored icon by file name; rejects anything that is not a stored icon name. */
    fun readIcon(iconFileName: String): ByteArray? {
        if (!ICON_NAME_REGEX.matches(iconFileName)) {
            return null
        }
        val path = iconDir.resolve(iconFileName).normalize()
        if (!path.startsWith(iconDir)) {
            return null
        }
        return if (Files.exists(path)) Files.readAllBytes(path) else null
    }

    private fun resolveWithinRoot(relativePath: String): Path {
        val path = root.resolve(relativePath).normalize()
        require(path.startsWith(root)) { "Path escapes storage root" }
        return path
    }

    companion object {
        private val ICON_NAME_REGEX = Regex("^[a-f0-9]{64}\\.(png|webp|jpg)$")
    }
}
