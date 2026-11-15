package com.apkrepo.backend.domain

enum class ReleaseStatus {
    /** Uploaded, validation/extraction in progress. */
    PROCESSING,

    /** Validated and stored, not yet visible in the F-Droid repository. */
    READY,

    /** Visible on the website and referenced by a published F-Droid index. */
    PUBLISHED,

    /** Analysis or publication failed; see [com.apkrepo.backend.domain.Release.errorMessage]. */
    FAILED,

    /** Withdrawn from the public catalog and repository. */
    UNPUBLISHED,
}
