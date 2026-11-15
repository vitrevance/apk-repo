package com.apkrepo.backend.api

import com.apkrepo.backend.api.dto.AppDetailDto
import com.apkrepo.backend.api.dto.AppSummaryDto
import com.apkrepo.backend.api.dto.PageResponse
import com.apkrepo.backend.api.dto.ReleaseDto
import com.apkrepo.backend.api.dto.toDetailDto
import com.apkrepo.backend.api.dto.toDto
import com.apkrepo.backend.api.dto.toResponse
import com.apkrepo.backend.api.dto.toSummaryDto
import com.apkrepo.backend.api.error.NotFoundException
import com.apkrepo.backend.domain.ReleaseStatus
import com.apkrepo.backend.repository.AppRepository
import com.apkrepo.backend.repository.ReleaseRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalog", description = "Public app catalog: browse, search and inspect releases")
class CatalogController(
    private val appRepository: AppRepository,
    private val releaseRepository: ReleaseRepository,
) {

    @GetMapping("/apps")
    @Operation(summary = "List apps", description = "Paginated catalog; optionally filtered by name or package ID")
    fun listApps(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(required = false) @Size(max = 255) query: String?,
    ): PageResponse<AppSummaryDto> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val apps = if (query.isNullOrBlank()) {
            appRepository.findAllWithAvailableReleases(PUBLICLY_VISIBLE, pageable)
        } else {
            appRepository.search(query, PUBLICLY_VISIBLE, pageable)
        }
        return apps.toResponse { app ->
            val latest = releaseRepository
                .findFirstByAppIdAndStatusInOrderByVersionCodeDesc(app.id, PUBLICLY_VISIBLE)
            app.toSummaryDto(latest, releaseRepository.countByAppId(app.id))
        }
    }

    @GetMapping("/apps/{packageName}")
    @Operation(summary = "App details", description = "Details for one app including its latest available release")
    fun getApp(@PathVariable packageName: String): AppDetailDto {
        val app = findApp(packageName)
        val latest = releaseRepository
            .findFirstByAppIdAndStatusInOrderByVersionCodeDesc(app.id, PUBLICLY_VISIBLE)
        return app.toDetailDto(latest)
    }

    @GetMapping("/apps/{packageName}/releases")
    @Operation(summary = "Release history", description = "Available versions of an app, newest version code first")
    fun listReleases(@PathVariable packageName: String): List<ReleaseDto> {
        val app = findApp(packageName)
        return releaseRepository
            .findByAppIdAndStatusInOrderByVersionCodeDesc(app.id, PUBLICLY_VISIBLE)
            .map { it.toDto() }
    }

    private fun findApp(packageName: String) =
        appRepository.findByPackageName(packageName)
            .orElseThrow { NotFoundException("App not found: $packageName") }

    companion object {
        /** Release states visible in the public catalog. */
        val PUBLICLY_VISIBLE = listOf(ReleaseStatus.READY, ReleaseStatus.PUBLISHED)
    }
}
