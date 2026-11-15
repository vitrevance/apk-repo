package com.apkrepo.backend.ingestion

/** Reasons an uploaded APK can be rejected during structural validation or analysis. */
enum class ApkErrorCode {
    /** Not a ZIP archive at all, or wrong file extension. */
    NOT_AN_APK,

    /** ZIP structure unreadable, truncated or corrupt. */
    CORRUPT_ARCHIVE,

    /** Entry name escapes the archive root (path traversal attempt). */
    ENTRY_TRAVERSAL,

    /** Too many ZIP entries. */
    ENTRY_COUNT_EXCEEDED,

    /** Compressed or declared decompressed size above the configured limits. */
    SIZE_EXCEEDED,

    /** No AndroidManifest.xml at the archive root. */
    MISSING_MANIFEST,

    /** No classes.dex; likely a split APK, which is unsupported (plan section 1). */
    MISSING_DEX,

    /** The APK carries no signature at all. */
    UNSIGNED_APK,

    /** Signature present but verification failed. */
    SIGNATURE_INVALID,
}
