package com.apkrepo.backend.api.error

import com.apkrepo.backend.ingestion.ApkErrorCode

/** Validation failure of an uploaded APK; maps to HTTP 422 with [code] as the error code. */
class ApkValidationException(val code: ApkErrorCode, message: String) : RuntimeException(message)

/** Conflict codes for release identity rules (plan section 4 constraints). */
enum class ConflictCode {
    /** Same package and version code already exists with different APK content. */
    DUPLICATE_VERSION,

    /** The APK's signing certificate differs from previous releases of the same package. */
    SIGNER_MISMATCH,
}

/** Release identity conflict; maps to HTTP 409 with [code] as the error code. */
class ConflictException(val code: ConflictCode, message: String) : RuntimeException(message)

/** Semantically invalid request that Bean Validation cannot express; maps to HTTP 400. */
class BadRequestException(message: String) : RuntimeException(message)
