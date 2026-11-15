package com.apkrepo.backend.api.dto

import com.apkrepo.backend.domain.Release
import com.apkrepo.backend.domain.ReleaseStatus
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Response for a (re-)upload; duplicate uploads are idempotent (plan §4). */
data class ReleaseUploadResultDto(
    /** True when the exact same APK bytes were already stored. */
    val duplicate: Boolean,
    /** True when this upload created the app's catalog entry. */
    val isNewApp: Boolean,
    val release: AdminReleaseDto,
)

/** Full release view for the admin endpoints, including failures. */
data class AdminReleaseDto(
    val id: UUID,
    val packageName: String,
    val displayName: String?,
    val versionName: String,
    val versionCode: Long,
    val status: ReleaseStatus,
    val apkSize: Long,
    val apkSha256: String,
    val apkSignerSha256: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val uploadedAt: Instant?,
    val publishedAt: Instant?,
    val errorMessage: String?,
    val downloadUrl: String,
)

/** PATCH body for catalog metadata edits; null fields are left unchanged. */
data class UpdateAppRequestDto(
    @field:Size(max = 255)
    val displayName: String? = null,

    @field:Size(max = 5000)
    val description: String? = null,
)

fun Release.toAdminDto(): AdminReleaseDto = AdminReleaseDto(
    id = id,
    packageName = app.packageName,
    displayName = app.displayName,
    versionName = versionName,
    versionCode = versionCode,
    status = status,
    apkSize = apkSize,
    apkSha256 = apkSha256,
    apkSignerSha256 = apkSignerSha256,
    minSdk = minSdk,
    targetSdk = targetSdk,
    uploadedAt = uploadedAt,
    publishedAt = publishedAt,
    errorMessage = errorMessage,
    downloadUrl = "/api/v1/releases/$id/download",
)
