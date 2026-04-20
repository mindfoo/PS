package org.workflow.utils

import org.springframework.http.ResponseEntity

/**
 * RFC 7807 Problem Details envelope.
 * All error responses are serialised with Content-Type: application/problem+json.
 *
 * Companion object pre-defines every named problem the API can return so that
 * controllers never construct ad-hoc error messages.
 */
data class Problem(val title: String) {

    companion object {
        const val MEDIA_TYPE = "application/problem+json"

        /** Builds a [ResponseEntity] that carries the problem as its body. */
        fun response(status: Int, problem: Problem): ResponseEntity<Any> =
            ResponseEntity.status(status)
                .header("Content-Type", MEDIA_TYPE)
                .body(problem)

        // ── Auth ──────────────────────────────────────────────────────────────
        val usernameAlreadyTaken = Problem("The username is already taken.")
        val roleNotFound = Problem("The specified role does not exist.")
        val invalidCredentials = Problem("The provided credentials are invalid.")
        val userNotFound = Problem("The requested user was not found.")

        // ── Workflows ─────────────────────────────────────────────────────────
        val workflowNotFound = Problem("The requested workflow was not found.")

        // ── Tasks ─────────────────────────────────────────────────────────────
        val taskNotFound = Problem("The requested task was not found.")

        // ── Schedules ─────────────────────────────────────────────────────────
        val scheduleNotFound = Problem("The requested schedule was not found.")
        val invalidCronExpression = Problem("The provided cron expression is invalid.")

        // ── Generic ───────────────────────────────────────────────────────────
        val invalidRequestContent = Problem("The request content is not valid.")
        val accessDenied = Problem("Access denied: insufficient permissions.")
        val internalError = Problem("An unexpected error occurred.")
    }
}

