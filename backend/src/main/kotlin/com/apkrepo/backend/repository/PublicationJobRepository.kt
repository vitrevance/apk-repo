package com.apkrepo.backend.repository

import com.apkrepo.backend.domain.PublicationAction
import com.apkrepo.backend.domain.PublicationJob
import com.apkrepo.backend.domain.PublicationJobState
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PublicationJobRepository : JpaRepository<PublicationJob, UUID> {

    fun countByState(state: PublicationJobState): Long

    fun findFirstByStateOrderByCreatedAtAsc(state: PublicationJobState): PublicationJob?

    /** Crash recovery: all jobs stuck in RUNNING at startup. */
    fun findByState(state: PublicationJobState): List<PublicationJob>

    /** Coalescing check: an equivalent PENDING job already targets this release. */
    fun findFirstByActionAndStateAndReleaseId(
        action: PublicationAction,
        state: PublicationJobState,
        releaseId: UUID,
    ): PublicationJob?

    /** Rebuild coalescing: any job still pending or running. */
    fun findFirstByStateInOrderByCreatedAtAsc(states: Collection<PublicationJobState>): PublicationJob?
}
