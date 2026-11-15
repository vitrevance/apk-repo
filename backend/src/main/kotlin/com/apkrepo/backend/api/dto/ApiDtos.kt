package com.apkrepo.backend.api.dto

import com.apkrepo.backend.domain.App
import com.apkrepo.backend.domain.Release
import org.springframework.data.domain.Page
import java.time.Instant
import java.util.UUID

data class AppSummaryDto(
    val packageName: String,
    val displayName: String?,
    val iconUrl: String?,
    val latestVersionName: String?,
    val latestVersionCode: Long?,
    val releaseCount: Long,
)

data class AppDetailDto(
    val packageName: String,
    val displayName: String?,
    val description: String?,
    val iconUrl: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val latestRelease: ReleaseDto?,
)

data class ReleaseDto(
    val id: UUID,
    val versionName: String,
    val versionCode: Long,
    val apkSize: Long,
    val apkSha256: String,
    val minSdk: Int?,
    val targetSdk: Int?,
    val status: com.apkrepo.backend.domain.ReleaseStatus,
    val uploadedAt: Instant?,
    val publishedAt: Instant?,
    val downloadUrl: String,
)

data class RepositoryInfoDto(
    val name: String,
    val url: String,
    val fingerprint: String?,
)

data class AdminStatsDto(
    val appCount: Long,
    val releaseCount: Long,
    val pendingJobCount: Long,
)

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

fun <T, R> Page<T>.toResponse(transform: (T) -> R): PageResponse<R> =
    PageResponse(
        content = content.map(transform),
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )

fun Release.toDto(): ReleaseDto = ReleaseDto(
    id = id,
    versionName = versionName,
    versionCode = versionCode,
    apkSize = apkSize,
    apkSha256 = apkSha256,
    minSdk = minSdk,
    targetSdk = targetSdk,
    status = status,
    uploadedAt = uploadedAt,
    publishedAt = publishedAt,
    downloadUrl = "/api/v1/releases/$id/download",
)

/** Icons are stored under a content-addressed name; expose the public URL. */
private fun App.iconUrl(): String? = iconPath?.let { "/api/v1/icons/$it" }

fun App.toSummaryDto(latestRelease: Release?, releaseCount: Long): AppSummaryDto = AppSummaryDto(
    packageName = packageName,
    displayName = displayName,
    iconUrl = iconUrl(),
    latestVersionName = latestRelease?.versionName,
    latestVersionCode = latestRelease?.versionCode,
    releaseCount = releaseCount,
)

fun App.toDetailDto(latestRelease: Release?): AppDetailDto = AppDetailDto(
    packageName = packageName,
    displayName = displayName,
    description = description,
    iconUrl = iconUrl(),
    createdAt = createdAt,
    updatedAt = updatedAt,
    latestRelease = latestRelease?.toDto(),
)
