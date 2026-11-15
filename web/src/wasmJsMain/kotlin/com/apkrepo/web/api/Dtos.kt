package com.apkrepo.web.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Client-side mirrors of the backend JSON DTOs. UUIDs and timestamps are
 * transported as strings (exactly how the backend serializes them).
 */
@Serializable
data class AppSummaryDto(
    val packageName: String,
    val displayName: String? = null,
    val iconUrl: String? = null,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val releaseCount: Long = 0,
)

@Serializable
data class ReleaseDto(
    val id: String,
    val versionName: String,
    val versionCode: Long,
    val apkSize: Long,
    val apkSha256: String,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val status: String = "PUBLISHED",
    val uploadedAt: String? = null,
    val publishedAt: String? = null,
    val downloadUrl: String,
)

@Serializable
data class AppDetailDto(
    val packageName: String,
    val displayName: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val latestRelease: ReleaseDto? = null,
)

@Serializable
data class RepositoryInfoDto(
    val name: String,
    val url: String,
    val fingerprint: String? = null,
)

@Serializable
data class AdminStatsDto(
    val appCount: Long = 0,
    val releaseCount: Long = 0,
    val pendingJobCount: Long = 0,
)

@Serializable
data class AdminReleaseDto(
    val id: String,
    val packageName: String,
    val displayName: String? = null,
    val versionName: String,
    val versionCode: Long,
    val status: String,
    val apkSize: Long,
    val apkSha256: String,
    val apkSignerSha256: String? = null,
    val minSdk: Int? = null,
    val targetSdk: Int? = null,
    val uploadedAt: String? = null,
    val publishedAt: String? = null,
    val errorMessage: String? = null,
    val downloadUrl: String,
)

@Serializable
data class UploadResultDto(
    val duplicate: Boolean = false,
    val isNewApp: Boolean = false,
    val release: AdminReleaseDto,
)

@Serializable
data class PublicationJobDto(
    val id: String,
    val action: String,
    val state: String,
    val releaseId: String? = null,
    val generationId: String? = null,
    val retryCount: Int = 0,
    val createdAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val errorMessage: String? = null,
)

@Serializable
data class EnqueueResultDto(
    val enqueued: Boolean = false,
    val job: PublicationJobDto? = null,
)

@Serializable
data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
    @SerialName("requestId") val requestId: String? = null,
)

@Serializable
data class PageResponseDto<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
)

@Serializable
data class UpdateAppRequest(
    val displayName: String? = null,
    val description: String? = null,
)
