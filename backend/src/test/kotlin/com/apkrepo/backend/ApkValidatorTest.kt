package com.apkrepo.backend

import com.apkrepo.backend.api.error.ApkValidationException
import com.apkrepo.backend.config.ApkRepoProperties
import com.apkrepo.backend.ingestion.ApkErrorCode
import com.apkrepo.backend.ingestion.ApkValidator
import com.apkrepo.backend.testutil.TestApkFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Unit tests for the untrusted-input ZIP validation rules (plan §5 step 2). */
class ApkValidatorTest {

    @TempDir
    lateinit var tempDir: Path

    private val validator = ApkValidator(ApkRepoProperties())

    private fun staged(bytes: ByteArray): Path {
        val file = Files.createTempFile(tempDir, "test", ".part")
        Files.write(file, bytes)
        return file
    }

    @Test
    fun `valid fixture passes and hash matches file digest`() {
        val apk = Files.createTempFile(tempDir, "app", ".apk")
        TestApkFactory.buildApk(apk)

        val hash = validator.validateAndHash(apk)

        val expected = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(apk))
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, hash)
    }

    @Test
    fun `garbage bytes are rejected as not an apk`() {
        val error = assertThrows(ApkValidationException::class.java) {
            validator.validateAndHash(staged(TestApkFactory.notAnApk()))
        }
        assertEquals(ApkErrorCode.NOT_AN_APK, error.code)
    }

    @Test
    fun `zip without manifest is rejected`() {
        val error = assertThrows(ApkValidationException::class.java) {
            validator.validateAndHash(staged(TestApkFactory.zipWithoutManifest()))
        }
        assertEquals(ApkErrorCode.MISSING_MANIFEST, error.code)
    }

    @Test
    fun `zip without dex is rejected as likely split apk`() {
        val error = assertThrows(ApkValidationException::class.java) {
            validator.validateAndHash(staged(TestApkFactory.zipWithoutDex()))
        }
        assertEquals(ApkErrorCode.MISSING_DEX, error.code)
    }

    @Test
    fun `traversal entry names are rejected`() {
        val error = assertThrows(ApkValidationException::class.java) {
            validator.validateAndHash(staged(TestApkFactory.zipWithTraversalEntry()))
        }
        assertEquals(ApkErrorCode.ENTRY_TRAVERSAL, error.code)
    }
}
