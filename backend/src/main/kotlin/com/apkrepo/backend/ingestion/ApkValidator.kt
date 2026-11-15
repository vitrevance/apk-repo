package com.apkrepo.backend.ingestion

import com.apkrepo.backend.api.error.ApkValidationException
import com.apkrepo.backend.config.ApkRepoProperties
import org.springframework.stereotype.Component
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Structural validation of an uploaded APK (plan §5 step 2). APKs are
 * untrusted data: limits are enforced from the ZIP central directory
 * (declared sizes) without decompressing entries, and entry names are
 * checked for traversal attempts.
 */
@Component
class ApkValidator(private val properties: ApkRepoProperties) {

    /**
     * Validates the staged file as an APK and returns its lowercase hex
     * SHA-256 digest. Throws [ApkValidationException] with a specific
     * [ApkErrorCode] on any rule violation.
     */
    fun validateAndHash(stagedFile: Path): String {
        val limits = properties.upload

        val zipFile = try {
            ZipFile(stagedFile.toFile())
        } catch (_: IOException) {
            throw ApkValidationException(
                ApkErrorCode.NOT_AN_APK,
                "File is not a valid ZIP/APK archive.",
            )
        }

        var hasManifest = false
        var hasDex = false
        var entryCount = 0
        var totalUncompressed = 0L

        zipFile.use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > limits.maxEntryCount) {
                    throw ApkValidationException(
                        ApkErrorCode.ENTRY_COUNT_EXCEEDED,
                        "Archive has more than ${limits.maxEntryCount} entries.",
                    )
                }
                val name = entry.name
                if (!isSafeEntryName(name)) {
                    throw ApkValidationException(
                        ApkErrorCode.ENTRY_TRAVERSAL,
                        "Archive entry name escapes the archive root: \"$name\".",
                    )
                }
                if (name == "AndroidManifest.xml") {
                    hasManifest = true
                } else if (name == "classes.dex" || (name.startsWith("classes") && name.endsWith(".dex"))) {
                    hasDex = true
                }
                if (!entry.isDirectory) {
                    val size = entry.size
                    if (size >= 0) {
                        if (size > limits.maxEntryUncompressedBytes) {
                            throw ApkValidationException(
                                ApkErrorCode.SIZE_EXCEEDED,
                                "Archive entry \"$name\" declares $size uncompressed bytes, " +
                                    "above the ${limits.maxEntryUncompressedBytes} byte limit.",
                            )
                        }
                        totalUncompressed += size
                        if (totalUncompressed > limits.maxTotalUncompressedBytes) {
                            throw ApkValidationException(
                                ApkErrorCode.SIZE_EXCEEDED,
                                "Archive declares more than ${limits.maxTotalUncompressedBytes} " +
                                    "total uncompressed bytes.",
                            )
                        }
                    }
                }
            }
        }

        if (!hasManifest) {
            throw ApkValidationException(
                ApkErrorCode.MISSING_MANIFEST,
                "APK does not contain a root-level AndroidManifest.xml.",
            )
        }
        if (!hasDex) {
            throw ApkValidationException(
                ApkErrorCode.MISSING_DEX,
                "APK contains no classes.dex; split APKs (.apks/.xapk/split) are not supported.",
            )
        }

        return sha256Of(stagedFile)
    }

    /** Rejects absolute paths, drive letters and dot-dot path segments. */
    private fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty()) return false
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/")) return false
        if (normalized.length > 1 && normalized[1] == ':') return false
        val segments = normalized.split('/')
        return segments.none { it == ".." }
    }

    private fun sha256Of(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
