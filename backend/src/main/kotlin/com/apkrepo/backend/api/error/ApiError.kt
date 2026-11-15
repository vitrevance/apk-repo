package com.apkrepo.backend.api.error

import java.time.Instant

/**
 * Consistent error payload returned by every REST endpoint on failure.
 */
data class ApiError(
    val status: Int,
    val code: String,
    val message: String?,
    val requestId: String,
    val timestamp: Instant,
)

/** Requested resource does not exist. Maps to HTTP 404 / `NOT_FOUND`. */
class NotFoundException(message: String) : RuntimeException(message)
