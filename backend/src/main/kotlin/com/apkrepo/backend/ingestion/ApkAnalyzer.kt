package com.apkrepo.backend.ingestion

import com.android.apksig.ApkVerifier
import com.apkrepo.backend.api.error.ApkValidationException
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Analyzes a structurally valid APK (plan §5 steps 3–5):
 * 1. verifies the Android signature with AOSP's apksig (never re-signs);
 * 2. extracts identity metadata from the binary AndroidManifest.xml;
 * 3. resolves the application label from the resource table;
 * 4. delegates icon extraction to [IconExtractor].
 *
 * Everything runs in-JVM: no Android SDK tools are needed in the API image.
 */
@Component
class ApkAnalyzer(private val iconExtractor: IconExtractor) {

    fun analyze(apkFile: Path): ApkMetadata {
        val signerSha256 = verifySignature(apkFile)

        val module = ApkModule.loadApkFile(apkFile.toFile())
        val manifest: AndroidManifestBlock = module.androidManifest
            ?: throw ApkValidationException(
                ApkErrorCode.MISSING_MANIFEST,
                "APK has no readable AndroidManifest.xml.",
            )
        try {
            val packageName = manifest.packageName
                ?: throw ApkValidationException(
                    ApkErrorCode.MISSING_MANIFEST,
                    "AndroidManifest.xml declares no package name.",
                )
            val versionName = manifest.versionName
                ?: throw ApkValidationException(
                    ApkErrorCode.MISSING_MANIFEST,
                    "AndroidManifest.xml declares no versionName.",
                )
            val versionCode = manifest.versionCode?.toLong()
                ?: throw ApkValidationException(
                    ApkErrorCode.MISSING_MANIFEST,
                    "AndroidManifest.xml declares no versionCode.",
                )

            val label = resolveLabel(module.tableBlock, manifest)
            val icon = iconExtractor.extract(module, manifest)

            return ApkMetadata(
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = manifest.minSdkVersion,
                targetSdk = manifest.targetSdkVersion,
                label = label,
                signerSha256 = signerSha256,
                icon = icon,
            )
        } finally {
            module.close()
        }
    }

    /**
     * Verifies the APK signature and returns the SHA-256 (lowercase hex) of
     * the signer certificate. The APK is never modified or re-signed.
     */
    private fun verifySignature(apkFile: Path): String {
        val result = ApkVerifier.Builder(apkFile.toFile()).build().verify()

        if (!result.isVerified) {
            if (result.signerCertificates.isEmpty()) {
                throw ApkValidationException(
                    ApkErrorCode.UNSIGNED_APK,
                    "APK is not signed; only signed, installable APKs are accepted.",
                )
            }
            val reason = result.errors.firstOrNull()?.toString() ?: "unknown verification failure"
            throw ApkValidationException(
                ApkErrorCode.SIGNATURE_INVALID,
                "APK signature verification failed: $reason",
            )
        }

        val certificates: List<X509Certificate> = result.signerCertificates
        val certificate = certificates.firstOrNull()
            ?: throw ApkValidationException(
                ApkErrorCode.SIGNATURE_INVALID,
                "APK signature verified but no signer certificate could be extracted.",
            )
        return sha256Hex(certificate.encoded)
    }

    /**
     * Resolves the application label: a literal manifest string takes
     * precedence; otherwise the referenced string resource is resolved for
     * the documented default locale ("en") with fallback to the default
     * configuration. Reference chains (e.g. app_name -> app_name_fdroid)
     * are followed with ARSCLib's cycle-safe [ResourceEntry.resolveReference].
     */
    private fun resolveLabel(tableBlock: TableBlock?, manifest: AndroidManifestBlock): String? {
        manifest.getApplicationLabelString()?.let { return it }
        val reference = manifest.getApplicationLabelReference() ?: return null
        val resourceEntry = tableBlock?.getResource(reference) ?: return null
        val resolved = try {
            resourceEntry.resolveReference()
        } catch (_: Exception) {
            resourceEntry
        }
        return readStringValue(resolved)
    }

    private fun readStringValue(resourceEntry: com.reandroid.arsc.model.ResourceEntry): String? = try {
        resourceEntry.get("-en")?.valueAsString
            ?: resourceEntry.get("")?.valueAsString
            ?: resourceEntry.any()?.valueAsString
    } catch (_: Exception) {
        null
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
