package com.apkrepo.backend.ingestion

/**
 * Metadata extracted from a validated, signed APK. All identity fields come
 * from the APK itself and are never editable through the catalog (plan §4:
 * display name/icon/description are catalog metadata, not app identity).
 */
data class ApkMetadata(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    /** Application label resolved for the documented default locale ("en"). */
    val label: String?,
    /** SHA-256 (lowercase hex) of the APK's signing certificate. */
    val signerSha256: String,
    /** Extracted browser-ready icon; null when only adaptive/vector icons exist. */
    val icon: ExtractedIcon?,
)

data class ExtractedIcon(
    val data: ByteArray,
    val mediaType: String,
    val extension: String,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
