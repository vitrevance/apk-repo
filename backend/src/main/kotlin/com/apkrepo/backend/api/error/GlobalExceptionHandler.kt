package com.apkrepo.backend.api.error

import com.apkrepo.backend.config.RequestIdFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant
import java.util.UUID

/**
 * Translates exceptions into the shared [ApiError] payload with a
 * request ID so failures can be correlated with logs and client reports.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private fun build(
        status: HttpStatus,
        code: String,
        message: String?,
        request: HttpServletRequest,
    ): ApiError {
        val requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE) as String?
            ?: request.getHeader(RequestIdFilter.HEADER_NAME)
            ?: UUID.randomUUID().toString()
        return ApiError(
            status = status.value(),
            code = code,
            message = message,
            requestId = requestId,
            timestamp = Instant.now(),
        )
    }

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.message, request))

    /**
     * Missing static resource (e.g. an F-Droid index or APK file that has
     * not been generated yet). Without this handler the exception would
     * fall through to the generic 500 backstop.
     */
    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.message, request))

    @ExceptionHandler(
        MethodArgumentNotValidException::class,
        MissingServletRequestParameterException::class,
        MethodArgumentTypeMismatchException::class,
        HandlerMethodValidationException::class,
        ConstraintViolationException::class,
    )
    fun handleValidation(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.message, request))

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.message, request))

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeNotSupported(
        ex: HttpMediaTypeNotSupportedException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", ex.message, request))

    @ExceptionHandler(AccessDeniedException::class, AuthorizationDeniedException::class)
    fun handleAccessDenied(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ex.message, request))

    /** Semantically invalid request (e.g. PATCH body with no fields). */
    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.message, request))

    /** Uploaded APK failed structural validation or analysis. */
    @ExceptionHandler(ApkValidationException::class)
    fun handleApkValidation(ex: ApkValidationException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(build(HttpStatus.UNPROCESSABLE_ENTITY, ex.code.name, ex.message, request))

    /** Release identity conflicts (duplicate version, signer mismatch). */
    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(build(HttpStatus.CONFLICT, ex.code.name, ex.message, request))

    /** Multipart upload above the configured size limit. */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUpload(ex: MaxUploadSizeExceededException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(
                build(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE",
                    "Uploaded file exceeds the configured size limit.",
                    request,
                ),
            )

    /** Race-safe backstop: concurrent uploads hitting unique constraints. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(ex: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ApiError> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(
                build(
                    HttpStatus.CONFLICT,
                    ConflictCode.DUPLICATE_VERSION.name,
                    "A conflicting record already exists; re-check the catalog.",
                    request,
                ),
            )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        log.error("Unhandled exception while processing {} {}", request.method, request.requestURI, ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                build(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INTERNAL_ERROR",
                    "An unexpected error occurred.",
                    request,
                ),
            )
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
