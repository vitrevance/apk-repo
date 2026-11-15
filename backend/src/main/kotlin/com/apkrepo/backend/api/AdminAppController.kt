package com.apkrepo.backend.api

import com.apkrepo.backend.api.dto.AppDetailDto
import com.apkrepo.backend.api.dto.UpdateAppRequestDto
import com.apkrepo.backend.ingestion.ApkIngestionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin Apps", description = "Catalog metadata editing; requires administrator authentication")
@SecurityRequirement(name = "basicAuth")
class AdminAppController(private val ingestionService: ApkIngestionService) {

    @PatchMapping("/apps/{packageName}")
    @Operation(
        summary = "Edit catalog metadata",
        description = "Changes the catalog display name and/or description. The app's identity " +
            "(package name, release identity) always stays derived from the APK and cannot be edited.",
    )
    fun updateApp(
        @PathVariable packageName: String,
        @Valid @RequestBody request: UpdateAppRequestDto,
    ): AppDetailDto = ingestionService.updateAppMetadata(packageName, request)
}
