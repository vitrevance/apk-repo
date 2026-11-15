package com.apkrepo.backend.domain

/** Lifecycle state of a publication job. Jobs are serialized (one active at a time). */
enum class PublicationJobState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
}
