package com.apkrepo.backend

import com.apkrepo.backend.config.ApkRepoProperties
import com.apkrepo.backend.ingestion.ApkAnalyzer
import com.apkrepo.backend.ingestion.IconExtractor
import com.apkrepo.backend.testutil.TestApkFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the pure-JVM APK analyzer: manifest identity extraction,
 * label resolution (default locale), signer hashing and icon extraction.
 */
class ApkAnalyzerTest {

    @TempDir
    lateinit var tempDir: Path

    private val analyzer = ApkAnalyzer(IconExtractor())

    private fun apk(name: String = "app.apk"): Path {
        val file = tempDir.resolve(name)
        return TestApkFactory.buildApk(file)
    }

    @Test
    fun `extracts identity fields from a signed fixture`() {
        val apk = apk()
        val metadata = analyzer.analyze(apk)

        assertEquals("com.example.alpha", metadata.packageName)
        assertEquals("1.0.0", metadata.versionName)
        assertEquals(100L, metadata.versionCode)
        assertEquals(24, metadata.minSdk)
        assertEquals(34, metadata.targetSdk)
        assertEquals("Alpha App", metadata.label)
        assertNotNull(metadata.signerSha256)
        assertEquals(64, metadata.signerSha256.length)
        assertTrue(metadata.signerSha256.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `same signer yields same certificate hash, different signer does not`() {
        val a = analyzer.analyze(apk("a.apk"))
        val b = analyzer.analyze(apk("b.apk"))
        assertEquals(a.signerSha256, b.signerSha256)

        val otherSignerApk = tempDir.resolve("other-signed.apk")
        TestApkFactory.buildApk(otherSignerApk, signer = TestApkFactory.OTHER_SIGNER)
        val c = analyzer.analyze(otherSignerApk)
        assertTrue(a.signerSha256 != c.signerSha256)
    }

    @Test
    fun `raster icon is extracted with the expected media type`() {
        val metadata = analyzer.analyze(apk())
        val icon = metadata.icon
        assertNotNull(icon)
        assertEquals("image/png", icon!!.mediaType)
        assertEquals("png", icon.extension)
        assertTrue(icon.data.size > 8)
    }

    @Test
    fun `adaptive icon falls back to placeholder (null) without failing`() {
        val file = tempDir.resolve("adaptive.apk")
        TestApkFactory.buildApk(file, adaptiveIcon = true)
        val metadata = analyzer.analyze(file)
        assertNull(metadata.icon)
        assertEquals("Alpha App", metadata.label)
    }

    @Test
    fun `localized label resolves for the default locale en`() {
        val file = tempDir.resolve("localized.apk")
        TestApkFactory.buildApk(
            file,
            label = null,
            localizedLabels = mapOf("en" to "English Label", "de" to "Deutsche App"),
        )
        val metadata = analyzer.analyze(file)
        assertEquals("English Label", metadata.label)
    }

    @Test
    fun `label alias reference chain resolves to the final string`() {
        val file = tempDir.resolve("aliased-label.apk")
        TestApkFactory.buildApk(file, label = "Aliased App", labelIsReference = true)
        val metadata = analyzer.analyze(file)
        assertEquals("Aliased App", metadata.label)
    }

    @Test
    fun `missing label resolves to null without failing`() {
        val file = tempDir.resolve("nolabel.apk")
        TestApkFactory.buildApk(file, label = null)
        val metadata = analyzer.analyze(file)
        assertNull(metadata.label)
    }

    @Test
    fun `unsigned apk is rejected`() {
        val file = tempDir.resolve("unsigned.apk")
        TestApkFactory.buildApk(file, signer = null)
        val error = org.junit.jupiter.api.Assertions.assertThrows(
            com.apkrepo.backend.api.error.ApkValidationException::class.java,
        ) {
            analyzer.analyze(file)
        }
        assertEquals(com.apkrepo.backend.ingestion.ApkErrorCode.UNSIGNED_APK, error.code)
    }
}
