package com.apkrepo.backend.api

import com.apkrepo.backend.api.dto.AdminStatsDto
import com.apkrepo.backend.domain.PublicationJobState
import com.apkrepo.backend.repository.AppRepository
import com.apkrepo.backend.repository.PublicationJobRepository
import com.apkrepo.backend.repository.ReleaseRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Administration endpoints; require administrator authentication")
@SecurityRequirement(name = "basicAuth")
class AdminController(
    private val appRepository: AppRepository,
    private val releaseRepository: ReleaseRepository,
    private val publicationJobRepository: PublicationJobRepository,
) {

    @GetMapping("/stats")
    @Operation(summary = "Catalog statistics", description = "Counts of apps, releases and pending publication jobs")
    fun stats(): AdminStatsDto = AdminStatsDto(
        appCount = appRepository.count(),
        releaseCount = releaseRepository.count(),
        pendingJobCount = publicationJobRepository.countByState(PublicationJobState.PENDING),
    )
}
