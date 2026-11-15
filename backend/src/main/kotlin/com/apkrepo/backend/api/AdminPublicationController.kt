package com.apkrepo.backend.api

import com.apkrepo.backend.api.dto.EnqueueResultDto
import com.apkrepo.backend.api.dto.PageResponse
import com.apkrepo.backend.api.dto.PublicationJobDto
import com.apkrepo.backend.api.dto.toDto
import com.apkrepo.backend.api.dto.toResponse
import com.apkrepo.backend.publisher.PublicationJobEnqueuer
import com.apkrepo.backend.repository.PublicationJobRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Publication", description = "F-Droid publication job management; requires administrator authentication")
@SecurityRequirement(name = "basicAuth")
class AdminPublicationController(
    private val jobRepository: PublicationJobRepository,
    private val enqueuer: PublicationJobEnqueuer,
) {

    @GetMapping("/publication-jobs")
    @Operation(
        summary = "List publication jobs",
        description = "All F-Droid publication jobs (PUBLISH / UNPUBLISH / REBUILD) with their state, " +
            "generation id, retry count and error details, newest first.",
    )
    fun listJobs(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<PublicationJobDto> =
        jobRepository
            .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
            .toResponse { it.toDto() }

    @PostMapping("/repository/rebuild")
    @Operation(
        summary = "Rebuild the F-Droid repository",
        description = "Enqueues a full repository regeneration (indexes, metadata, icons) from the " +
            "current database state. Coalesced into an existing pending/running job when present.",
    )
    fun rebuild(): ResponseEntity<EnqueueResultDto> {
        val outcome = enqueuer.enqueueRebuild()
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(EnqueueResultDto(enqueued = outcome.created, job = outcome.job.toDto()))
    }
}
