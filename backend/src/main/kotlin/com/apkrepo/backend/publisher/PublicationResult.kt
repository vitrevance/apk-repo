package com.apkrepo.backend.publisher

import java.util.UUID

/**
 * Hard failure inside the publication pipeline. [cliOutput] carries the
 * fdroidserver subprocess output (when the failure came from the CLI) so
 * it can be persisted on the failed job for the admin to inspect.
 */
class PublisherException(
    message: String,
    val cliOutput: String? = null,
) : RuntimeException(message)

/** What one successful publication run produced. */
data class PublicationOutcome(
    val generationId: String?,
    val publishedReleaseIds: List<UUID>,
    val fingerprint: String?,
)
