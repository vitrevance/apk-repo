package com.apkrepo.web.api

import kotlinx.browser.window
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.w3c.fetch.Response
import org.w3c.files.File

/** Thrown when an admin request returns 401 — the session was dropped. */
class UnauthorizedException : Exception("Not signed in")

/** Thrown for non-2xx responses; carries the server message when parseable. */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Thin typed wrapper around the backend REST API (same origin, so no CORS
 * and no base URL is needed).
 */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    private suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        admin: Boolean = false,
    ): Response {
        val init = newRequestInit()
        init.method = method
        if (body != null || admin) {
            val headers = newHeadersObject()
            if (body != null) setHeader(headers, "Content-Type", "application/json")
            AdminSession.authHeader()?.let { setHeader(headers, "Authorization", it) }
            init.headers = headers
        }
        body?.let { init.body = it.toJsString() }
        val resp = window.fetch(path, init).await()
        if (admin && resp.status == 401.toShort()) {
            AdminSession.logout()
            throw UnauthorizedException()
        }
        if (!resp.ok) {
            throw ApiException(resp.status.toInt(), errorMessage(resp))
        }
        return resp
    }

    private suspend fun errorMessage(resp: Response): String {
        val text = try {
            jsToString(resp.text().await())
        } catch (e: Throwable) {
            return "request failed"
        }
        val parsed = runCatching { json.decodeFromString<ApiErrorDto>(text) }.getOrNull()
        return (parsed?.message ?: text.take(200)).ifBlank { "request failed" }
    }

    private suspend inline fun <reified T> decode(resp: Response): T =
        json.decodeFromString(jsToString(resp.text().await()))

    // ---- public endpoints ----

    suspend fun listApps(query: String?, page: Int = 0): PageResponseDto<AppSummaryDto> =
        decode(
            request(
                "GET",
                "/api/v1/apps?" + buildList {
                    query?.takeIf { it.isNotBlank() }?.let { add("query=" + encodeURIComponent(it)) }
                    add("page=$page")
                }.joinToString("&"),
            ),
        )

    suspend fun getApp(packageName: String): AppDetailDto =
        decode(request("GET", "/api/v1/apps/$packageName"))

    suspend fun listReleases(packageName: String): List<ReleaseDto> =
        decode(request("GET", "/api/v1/apps/$packageName/releases"))

    suspend fun repositoryInfo(): RepositoryInfoDto =
        decode(request("GET", "/api/v1/repository"))

    // ---- admin endpoints (require Basic auth) ----

    suspend fun adminStats(): AdminStatsDto =
        decode(request("GET", "/api/v1/admin/stats", admin = true))

    suspend fun adminListReleases(status: String? = null, page: Int = 0): PageResponseDto<AdminReleaseDto> =
        decode(
            request(
                "GET",
                "/api/v1/admin/releases?" + buildList {
                    status?.takeIf { it.isNotBlank() }?.let { add("status=$it") }
                    add("page=$page")
                }.joinToString("&"),
                admin = true,
            ),
        )

    suspend fun uploadApk(file: File): UploadResultDto {
        val form = newFormData()
        formDataAppendFile(form, "file", file)
        val init = newRequestInit()
        init.method = "POST"
        AdminSession.authHeader()?.let {
            val headers = newHeadersObject()
            setHeader(headers, "Authorization", it)
            init.headers = headers
        }
        init.body = form
        val resp = window.fetch("/api/v1/admin/releases", init).await()
        if (resp.status == 401.toShort()) {
            AdminSession.logout()
            throw UnauthorizedException()
        }
        if (!resp.ok) {
            throw ApiException(resp.status.toInt(), errorMessage(resp))
        }
        return decode(resp)
    }

    suspend fun updateApp(packageName: String, request: UpdateAppRequest): AppDetailDto =
        decode(
            request(
                "PATCH",
                "/api/v1/admin/apps/$packageName",
                body = json.encodeToString(request),
                admin = true,
            ),
        )

    suspend fun unpublishRelease(releaseId: String) {
        request("DELETE", "/api/v1/admin/releases/$releaseId", admin = true)
    }

    suspend fun listJobs(page: Int = 0): PageResponseDto<PublicationJobDto> =
        decode(request("GET", "/api/v1/admin/publication-jobs?page=$page", admin = true))

    suspend fun rebuildRepository(): EnqueueResultDto =
        decode(request("POST", "/api/v1/admin/repository/rebuild", admin = true))

    // ---- binary fetch (icons, QR code) ----

    suspend fun fetchBytes(url: String): ByteArray? =
        try {
            val resp = window.fetch(url).await()
            if (!resp.ok) return null
            arrayBufferToByteArray(resp.arrayBuffer().await())
        } catch (e: Throwable) {
            null
        }
}
