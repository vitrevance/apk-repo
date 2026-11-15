package com.apkrepo.backend.api

import com.apkrepo.backend.api.dto.RepositoryInfoDto
import com.apkrepo.backend.config.ApkRepoProperties
import com.apkrepo.backend.publisher.FdroidPublisher
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Repository", description = "F-Droid repository onboarding information")
class RepositoryInfoController(
    private val properties: ApkRepoProperties,
    private val publisher: FdroidPublisher,
) {

    @GetMapping("/repository")
    @Operation(
        summary = "Repository information",
        description = "Name, URL and signing-certificate fingerprint of the F-Droid repository. " +
            "The fingerprint is computed live from the signed entry.jar once the publisher has " +
            "initialized the repository signing key; a configured fingerprint property acts as " +
            "a manual override before that.",
    )
    fun repositoryInfo(): RepositoryInfoDto = RepositoryInfoDto(
        name = properties.repository.name,
        url = properties.repository.baseUrl.trimEnd('/') + "/fdroid/repo/",
        fingerprint = publisher.currentFingerprint() ?: properties.repository.fingerprint,
    )
}
