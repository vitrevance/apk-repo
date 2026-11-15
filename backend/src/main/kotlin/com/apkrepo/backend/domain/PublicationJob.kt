package com.apkrepo.backend.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

/**
 * A unit of work for the F-Droid publisher worker. Jobs are processed
 * serially; a persisted [generationId] lets an interrupted run be
 * recovered and retried safely.
 */
@Entity
@Table(name = "publication_jobs")
class PublicationJob(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    val action: PublicationAction,

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    var state: PublicationJobState = PublicationJobState.PENDING,

    /** Release this job was enqueued for, if the action targets one. */
    @Column(name = "release_id")
    val releaseId: UUID? = null,

    /** Identifier of the repository generation being produced. */
    @Column(name = "generation_id", length = 64)
    var generationId: String? = null,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,
) {
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant? = null
        private set

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "finished_at")
    var finishedAt: Instant? = null

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null
}
