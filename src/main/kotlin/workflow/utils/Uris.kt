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
        const val ME = "$PREFIX/auth/me"
    }

    object Users {
        const val REGISTER = "$PREFIX/users/register"
    }

    object Workflows {
        const val BASE = "$PREFIX/workflows"
        const val BY_ID = "$PREFIX/workflows/{id}"
        const val RUN = "$PREFIX/workflows/{id}/run"

        fun byId(id: UUID): URI = UriTemplate(BY_ID).expand(id)
    }

    object Tasks {
        const val BASE = "$PREFIX/tasks"
        const val BY_ID = "$PREFIX/tasks/{id}"

        fun byId(id: UUID): URI = UriTemplate(BY_ID).expand(id)
    }

    object Schedules {
        const val BASE = "$PREFIX/schedules"
        const val BY_ID = "$PREFIX/schedules/{id}"

        fun byId(id: UUID): URI = UriTemplate(BY_ID).expand(id)
    }
}

