package com.example.demo.exception

import com.example.demo.dto.ApiErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler(private val messageSource: MessageSource) {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    private fun getMessage(code: String, defaultMessage: String): String {
        return messageSource.getMessage(code, null, defaultMessage, LocaleContextHolder.getLocale()) ?: defaultMessage
    }

    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.CONFLICT, getMessage("error.duplicate_resource", ex.message ?: "Resource already exists"), request)
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.NOT_FOUND, getMessage("error.not_found", ex.message ?: "Resource not found"), request)
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.UNAUTHORIZED, getMessage("error.unauthorized", ex.message ?: "Unauthorized"), request)
    }

    @ExceptionHandler(PasswordMismatchException::class)
    fun handlePasswordMismatch(ex: PasswordMismatchException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, getMessage("error.password_mismatch", ex.message ?: "Password mismatch"), request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        val errors = ex.bindingResult.allErrors.mapNotNull { it.defaultMessage }
        val response = ApiErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = getMessage("error.validation_failed", "Validation failed"),
            path = request.requestURI,
            validationErrors = errors
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, getMessage("error.malformed_json", "Malformed JSON request"), request)
    }

    @ExceptionHandler(AppException::class)
    fun handleAppException(ex: AppException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, getMessage("error.invalid_request", ex.message ?: "Invalid request"), request)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, getMessage("error.invalid_arguments", ex.message ?: "Invalid arguments provided"), request)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllOtherExceptions(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        logger.error("Unexpected error occurred at ${request.requestURI}", ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, getMessage("error.internal_server_error", "An internal server error occurred"), request)
    }

    private fun buildResponse(status: HttpStatus, message: String, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        val response = ApiErrorResponse(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI
        )
        return ResponseEntity.status(status).body(response)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<Map<String, Any?>> {
        val errorResponse = mapOf(
            "timestamp" to java.time.Instant.now(),
            "status" to HttpStatus.FORBIDDEN.value(),
            "error" to "Forbidden",
            "message" to getMessage("error.forbidden", "Access Denied: You do not have permission to access this resource"),
            "validationErrors" to null
        )
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse)
    }
}