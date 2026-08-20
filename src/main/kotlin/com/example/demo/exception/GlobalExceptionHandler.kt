package com.example.demo.exception

import com.example.demo.dto.ApiErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 1. Duplicate resource (e.g. email or license already registered)
    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.CONFLICT, ex.message ?: "Resource already exists", request)
    }

    // 2. Resource not found
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.NOT_FOUND, ex.message ?: "Resource not found", request)
    }

    // 3. Invalid login credentials
    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentials(ex: InvalidCredentialsException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized", request)
    }

    // 4. Password mismatch
    @ExceptionHandler(PasswordMismatchException::class)
    fun handlePasswordMismatch(ex: PasswordMismatchException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Password mismatch", request)
    }

    // 5. Validation errors (bean validation)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        val errors = ex.bindingResult.allErrors.mapNotNull { it.defaultMessage }
        val response = ApiErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "Validation failed",
            path = request.requestURI,
            validationErrors = errors
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    // 6. Malformed JSON
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed JSON request", request)
    }

    // 7. Catch-all for unexpected errors
    @ExceptionHandler(Exception::class)
    fun handleAllOtherExceptions(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        logger.error("Unexpected error occurred at ${request.requestURI}", ex)
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An internal server error occurred", request)
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
}