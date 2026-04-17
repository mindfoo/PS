package org.workflow.config

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.workflow.utils.Problem

/**
 * Centralised exception handler that maps domain exceptions to RFC 7807 Problem responses.
 * Prevents raw stack traces from leaking into API clients.
 */
@RestControllerAdvice
@Suppress("unused")
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 400 — validation failures from @Valid annotated request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Any> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        log.debug("Validation error: {}", message)
        return Problem.response(400, Problem(message))
    }

    /** 400 — business-rule violations thrown from service layer. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Any> {
        log.debug("Bad request: {}", ex.message)
        return Problem.response(400, Problem(ex.message ?: "Bad request"))
    }

    /** 404 — resource not found, typically from service findById queries. */
    @ExceptionHandler(NoSuchElementException::class)
    fun handleNotFound(ex: NoSuchElementException): ResponseEntity<Any> {
        log.debug("Not found: {}", ex.message)
        return Problem.response(404, Problem(ex.message ?: "Not found"))
    }

    /** 403 — authenticated user lacks the required permission. */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(ex: AccessDeniedException): ResponseEntity<Any> {
        log.debug("Access denied: {}", ex.message)
        return Problem.response(403, Problem.accessDenied)
    }

    /** 500 — unhandled exceptions; logged server-side but not exposed in full to the client. */
    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<Any> {
        log.error("Unhandled exception: {}", ex.message, ex)
        return Problem.response(500, Problem.internalError)
    }
}
