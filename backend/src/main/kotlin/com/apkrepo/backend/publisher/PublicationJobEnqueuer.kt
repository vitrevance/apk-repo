package com.apkrepo.backend.publisher

import com.apkrepo.backend.domain.PublicationAction
import com.apkrepo.backend.domain.PublicationJob
import com.apkrepo.backend.domain.PublicationJobState
import com.apkrepo.backend.repository.PublicationJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Result of an enqueue call: the job plus whether a new row was created. */
data class EnqueueOutcome(
    val job: PublicationJob,
    val created: Boolean,
)

/**
 * Enqueues publication jobs with coalescing so bursts of changes never
 * pile up: a targeted PUBLISH/UNPUBLISH is skipped when an equivalent
 * PENDING job already exists, and a REBUILD is skipped whenever any job
 * is still pending or running, because every job regenerates the whole
 * repository anyway.
 */
@Service
class PublicationJobEnqueuer(
    private val jobRepository: PublicationJobRepository,
) {

    @Transactional
    fun enqueuePublish(releaseId: UUID): EnqueueOutcome =
        enqueueTargeted(PublicationAction.PUBLISH, releaseId)

    @Transactional
    fun enqueueUnpublish(releaseId: UUID): EnqueueOutcome =
        enqueueTargeted(PublicationAction.UNPUBLISH, releaseId)

    @Transactional
    fun enqueueRebuild(): EnqueueOutcome {
        val active = jobRepository.findFirstByStateInOrderByCreatedAtAsc(
            listOf(PublicationJobState.PENDING, PublicationJobState.RUNNING),
        )
        if (active != null) {
            return EnqueueOutcome(active, created = false)
        }
        return EnqueueOutcome(
            jobRepository.save(PublicationJob(action = PublicationAction.REBUILD)),
            created = true,
        )
    }

    private fun enqueueTargeted(action: PublicationAction, releaseId: UUID): EnqueueOutcome {
        val existing = jobRepository.findFirstByActionAndStateAndReleaseId(
            action,
            PublicationJobState.PENDING,
            releaseId,
        )
        if (existing != null) {
            return EnqueueOutcome(existing, created = false)
        }
        return EnqueueOutcome(
            jobRepository.save(PublicationJob(action = action, releaseId = releaseId)),
            created = true,
        )
    }
}
