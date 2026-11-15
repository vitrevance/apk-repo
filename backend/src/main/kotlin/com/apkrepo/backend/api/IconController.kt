package com.apkrepo.backend.api

import com.apkrepo.backend.api.error.NotFoundException
import com.apkrepo.backend.storage.StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/v1/icons")
@Tag(name = "Icons", description = "Public app icons, content-addressed and immutable")
class IconController(private val storageService: StorageService) {

    @GetMapping("/{fileName}")
    @Operation(
        summary = "Fetch an app icon",
        description = "Icon files are named by the SHA-256 of the APK they were extracted from, " +
            "so their content is immutable and safely cacheable.",
    )
    fun icon(@PathVariable fileName: String): ResponseEntity<ByteArray> {
        val data = storageService.readIcon(fileName)
            ?: throw NotFoundException("Icon not found: $fileName")

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(guessMediaType(fileName)))
            .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
            .body(data)
    }

    private fun guessMediaType(fileName: String): String = when {
        fileName.endsWith(".png") -> "image/png"
        fileName.endsWith(".webp") -> "image/webp"
        else -> "image/jpeg"
    }
}
