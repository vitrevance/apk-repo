package com.apkrepo.backend.api.dto

import com.apkrepo.backend.domain.PublicationAction
import com.apkrepo.backend.domain.PublicationJob
import com.apkrepo.backend.domain.PublicationJobState
import java.time.Instant
import java.util.UUID

data class PublicationJobDto(
    val id: UUID,
    val action: PublicationAction,
    val state: PublicationJobState,
    val releaseId: UUID?,
    val generationId: String?,
    val retryCount: Int,
    val createdAt: Instant?,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val errorMessage: String?,
)

/** Result of an enqueue/rebuild request (coalesced jobs return created=false). */
data class EnqueueResultDto(
    val enqueued: Boolean,
    val job: PublicationJobDto?,
)

fun PublicationJob.toDto(): PublicationJobDto = PublicationJobDto(
    id = id,
    action = action,
    state = state,
    releaseId = releaseId,
    generationId = generationId,
    retryCount = retryCount,
    createdAt = createdAt,
    startedAt = startedAt,
    finishedAt = finishedAt,
    errorMessage = errorMessage,
)
