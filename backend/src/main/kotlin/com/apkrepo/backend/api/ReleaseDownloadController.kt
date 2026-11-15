package com.apkrepo.backend.api

import com.apkrepo.backend.ingestion.ApkIngestionService
import com.apkrepo.backend.storage.StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Downloads", description = "Public APK downloads")
class ReleaseDownloadController(
    private val ingestionService: ApkIngestionService,
    private val storageService: StorageService,
) {

    @GetMapping("/releases/{releaseId}/download")
    @Operation(
        summary = "Download an APK",
        description = "Streams the original, unmodified APK bytes of a READY or PUBLISHED release. " +
            "The ETag is the APK's SHA-256 digest.",
    )
    fun download(@PathVariable releaseId: UUID): ResponseEntity<InputStreamResource> {
        val info = ingestionService.downloadInfo(releaseId)
        val stream = storageService.openApkStream(info.apkPath)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
            .eTag("\"${info.apkSha256}\"")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${info.fileName}\"")
            .contentLength(info.apkSize)
            .body(InputStreamResource(stream))
    }
}
