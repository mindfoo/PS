package org.workflow.utils

import org.springframework.web.util.UriTemplate
import java.net.URI
import java.util.UUID

/**
 * Single source of truth for all API path constants.
 * Controllers reference these strings in their mapping annotations so that
 * renaming a route only requires one edit here.
 */
object Uris {
    const val PREFIX = "/api"

    object Auth {
        const val REGISTER = "$PREFIX/auth/register"
        const val LOGIN = "$PREFIX/auth/login"
        const val LOGOUT = "$PREFIX/auth/logout"
        const val PROFILE = "$PREFIX/auth/profile"
    }

    object Users {
        const val BASE = "$PREFIX/users"
        const val ROLES = "$PREFIX/users/roles"
        const val UPDATE_ROLE = "$PREFIX/users/{id}/role"
    }

    object Workflows {
        const val BASE = "$PREFIX/workflows"
        const val BY_ID = "$PREFIX/workflows/{id}"
        const val RUN = "$PREFIX/workflows/{id}/run"
        const val TASK_ORDER = "$PREFIX/workflows/{id}/task-order"
        const val TASK_RETRY_POLICY = "$PREFIX/workflows/{id}/tasks/{taskId}/retry-policy"
        const val EXECUTIONS = "$PREFIX/workflows/{id}/executions"
        const val LINK_TASK = "$PREFIX/workflows/{id}/link-task/{taskId}"

        fun byId(id: UUID): URI = UriTemplate(BY_ID).expand(id)
    }

    object Executions {
        const val BY_ID = "$PREFIX/executions/{id}"
        const val CANCEL = "$PREFIX/executions/{id}/cancel"
        const val EVENTS = "$PREFIX/executions/{id}/events"
    }

    object Tasks {
        const val BASE = "$PREFIX/tasks"
        const val BY_ID = "$PREFIX/tasks/{id}"
        const val RUN = "$PREFIX/tasks/{id}/run"
        const val AVAILABLE_SCRIPTS = "$PREFIX/tasks/scripts"

        fun byId(id: UUID): URI = UriTemplate(BY_ID).expand(id)
    }

    object Schedules {
        const val BASE = "$PREFIX/schedules"
        const val BY_ID = "$PREFIX/schedules/{id}"

        fun byId(id: UUID): URI = UriTemplate(BY_ID).expand(id)
    }
}

