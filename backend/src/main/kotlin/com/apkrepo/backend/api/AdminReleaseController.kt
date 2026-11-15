package com.apkrepo.backend.api

import com.apkrepo.backend.api.dto.AdminReleaseDto
import com.apkrepo.backend.api.dto.PageResponse
import com.apkrepo.backend.api.dto.ReleaseUploadResultDto
import com.apkrepo.backend.api.dto.toResponse
import com.apkrepo.backend.domain.ReleaseStatus
import com.apkrepo.backend.ingestion.ApkIngestionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Releases", description = "APK upload and release management; requires administrator authentication")
@SecurityRequirement(name = "basicAuth")
class AdminReleaseController(private val ingestionService: ApkIngestionService) {

    @PostMapping("/releases", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(
        summary = "Upload an APK",
        description = "Multipart upload of a signed, installable .apk. The system validates the archive, " +
            "verifies the signature, extracts package/version/label/SDK/icon metadata, stores the original " +
            "bytes immutably and creates a READY release. Byte-identical re-uploads return the existing " +
            "release (duplicate=true).",
    )
    fun upload(@RequestPart("file") file: MultipartFile): ResponseEntity<ReleaseUploadResultDto> {
        val result = ingestionService.upload(file)
        val status = if (result.duplicate) HttpStatus.OK else HttpStatus.CREATED
        return ResponseEntity.status(status).body(result)
    }

    @GetMapping("/releases")
    @Operation(summary = "List releases", description = "All releases including failures; optional status filter")
    fun listReleases(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(required = false) status: ReleaseStatus?,
    ): PageResponse<AdminReleaseDto> = ingestionService.listReleases(page, size, status).toResponse { it }

    @GetMapping("/releases/{id}")
    @Operation(summary = "Release status", description = "Processing status, extracted metadata and failure details")
    fun getRelease(@PathVariable id: UUID): AdminReleaseDto = ingestionService.getRelease(id)

    @DeleteMapping("/releases/{id}")
    @Operation(
        summary = "Unpublish a release",
        description = "Marks the release UNPUBLISHED and removes it from the public catalog; " +
            "the APK file is retained until repository regeneration and garbage collection run (Phase 3).",
    )
    fun unpublish(@PathVariable id: UUID): ResponseEntity<Void> {
        ingestionService.unpublishRelease(id)
        return ResponseEntity.noContent().build()
    }
}
