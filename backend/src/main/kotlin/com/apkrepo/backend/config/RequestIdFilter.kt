package com.apkrepo.backend.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Assigns a request ID to every request. The value is taken from an
 * incoming `X-Request-Id` header when present (so callers can correlate
 * their own logs) and is otherwise generated. It is echoed back on the
 * response and included in error payloads produced by
 * [com.apkrepo.backend.api.error.GlobalExceptionHandler].
 */
@Component
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val requestId = request.getHeader(HEADER_NAME)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId)
        response.setHeader(HEADER_NAME, requestId)
        chain.doFilter(request, response)
    }

    companion object {
        const val HEADER_NAME = "X-Request-Id"
        const val REQUEST_ID_ATTRIBUTE = "apkRepo.requestId"
    }
}
