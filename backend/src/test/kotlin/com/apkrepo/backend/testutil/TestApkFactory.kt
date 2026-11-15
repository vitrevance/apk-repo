package com.apkrepo.backend.testutil

import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkModule
import com.reandroid.apk.AndroidFrameworks
import com.reandroid.archive.ByteInputSource
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates APK test fixtures entirely in-JVM: the APK structure (binary
 * AndroidManifest + resource table + dex) is built with ARSCLib and then
 * signed with AOSP's apksig. This covers the plan's Phase 2 fixture list
 * (valid, multi-version, adaptive icon, non-English label, malformed,
 * unsigned, split) without any Android SDK installation.
 */
object TestApkFactory {

    data class Signer(val privateKey: PrivateKey, val certificate: X509Certificate)

    /** A tiny valid PNG (1x1 red pixel). */
    val SAMPLE_PNG: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
        0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
        0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, 0xD7.toByte(), 0x63,
        0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00, 0x00, 0x03, 0x01, 0x01,
        0x00, 0x18, 0xDD.toByte(), 0x8D.toByte(), 0xB0.toByte(), 0x00, 0x00, 0x00, 0x00,
        0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
    )

    /** Minimal adaptive-icon XML placeholder (points at no renderable file). */
    private val ADAPTIVE_ICON_XML = """
        <?xml version="1.0" encoding="utf-8"?>
        <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
            <foreground android:drawable="@drawable/ic_fg"/>
        </adaptive-icon>
    """.trimIndent().toByteArray()

    /** Not real DEX bytecode, but a present, non-empty classes.dex entry. */
    private val DUMMY_DEX = ByteArray(64) { 0xAA.toByte() }

    /** Self-signed RSA certificate; different invocations produce different keys. */
    fun newSigner(cn: String = "apk-repo-test"): Signer {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=$cn")
        val now = Instant.now()
        val holder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(System.currentTimeMillis()),
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(3650, ChronoUnit.DAYS)),
            name,
            keyPair.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private))
        return Signer(keyPair.private, JcaX509CertificateConverter().getCertificate(holder))
    }

    /**
     * Builds and (optionally) signs an APK fixture. [contentSalt] changes the
     * bytes without changing the identity fields, e.g. to test the
     * same-version/different-content conflict.
     */
    fun buildApk(
        file: Path,
        signer: Signer? = DEFAULT_SIGNER,
        packageName: String = "com.example.alpha",
        versionName: String = "1.0.0",
        versionCode: Int = 100,
        label: String? = "Alpha App",
        labelIsReference: Boolean = false,
        localizedLabels: Map<String, String> = emptyMap(),
        minSdk: Int? = 24,
        targetSdk: Int? = 34,
        includeDex: Boolean = true,
        includeIcon: Boolean = true,
        adaptiveIcon: Boolean = false,
        contentSalt: ByteArray = ByteArray(0),
    ): Path {
        val module = ApkModule()
        val tableBlock = TableBlock()
        val manifest = AndroidManifestBlock()
        module.tableBlock = tableBlock
        module.setManifest(manifest)
        module.initializeAndroidFramework(AndroidFrameworks.getLatest().versionCode)

        val packageBlock = tableBlock.newPackage(0x7f, packageName)

        if (label != null || localizedLabels.isNotEmpty()) {
            val defaultLabel = packageBlock.getOrCreate("", "string", "app_name")
            if (labelIsReference) {
                // Real apps often alias: app_name -> app_name_real (REFERENCE value).
                val target = packageBlock.getOrCreate("", "string", "app_name_real")
                target.setValueAsString(label ?: packageName)
                defaultLabel.setValueAsReference(target.resourceId)
            } else {
                defaultLabel.setValueAsString(label ?: packageName)
            }
            for ((qualifier, value) in localizedLabels) {
                packageBlock.getOrCreate("-$qualifier", "string", "app_name").setValueAsString(value)
            }
            manifest.setApplicationLabel(defaultLabel.resourceId)
        }

        if (includeIcon) {
            val qualifier = if (adaptiveIcon) "-anydpi-v26" else "-xxxhdpi"
            val fileName = if (adaptiveIcon) "ic_launcher.xml" else "ic_launcher.png"
            val iconPath = "res/mipmap${qualifier}/$fileName"
            val iconEntry = packageBlock.getOrCreate(qualifier, "mipmap", "ic_launcher")
            iconEntry.setValueAsString(iconPath)
            module.add(ByteInputSource(if (adaptiveIcon) ADAPTIVE_ICON_XML else SAMPLE_PNG, iconPath))
            manifest.iconResourceId = iconEntry.resourceId
        }

        if (includeDex) {
            module.add(ByteInputSource(DUMMY_DEX, "classes.dex"))
        }
        if (contentSalt.isNotEmpty()) {
            module.add(ByteInputSource(contentSalt, "assets/salt.bin"))
        }

        manifest.packageName = packageName
        manifest.versionCode = versionCode
        manifest.versionName = versionName
        minSdk?.let { manifest.setMinSdkVersion(it) }
        targetSdk?.let { manifest.setTargetSdkVersion(it) }

        module.writeApk(file.toFile())

        if (signer != null) {
            sign(file, signer)
        }
        return file
    }

    private fun sign(file: Path, signer: Signer) {
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "test",
            signer.privateKey,
            listOf(signer.certificate),
        ).build()
        val signed = file.resolveSibling("${file.fileName}.signed")
        ApkSigner.Builder(listOf(signerConfig))
            .setMinSdkVersion(24)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setInputApk(file.toFile())
            .setOutputApk(signed.toFile())
            .build()
            .sign()
        Files.move(signed, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }

    /** Random garbage bytes: not a ZIP archive at all. */
    fun notAnApk(): ByteArray = ByteArray(2048) { (it * 31 + 7).toByte() }

    /** Valid ZIP without AndroidManifest.xml. */
    fun zipWithoutManifest(): ByteArray = zipOf("hello.txt" to "hello".toByteArray())

    /** Valid ZIP with a manifest and dex but a traversal entry name. */
    fun zipWithTraversalEntry(): ByteArray = zipOf(
        "AndroidManifest.xml" to ByteArray(16),
        "classes.dex" to DUMMY_DEX,
        "../evil.txt" to "evil".toByteArray(),
    )

    /** Valid ZIP with a manifest but no dex (split-APK-like). */
    fun zipWithoutDex(): ByteArray = zipOf(
        "AndroidManifest.xml" to ByteArray(16),
        "assets/data.bin" to ByteArray(16),
    )

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    val DEFAULT_SIGNER: Signer by lazy { newSigner("apk-repo-default") }
    val OTHER_SIGNER: Signer by lazy { newSigner("apk-repo-other") }
}
