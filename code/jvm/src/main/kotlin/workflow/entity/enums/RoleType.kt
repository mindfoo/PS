package org.workflow.entity.enums

/** Identifies the platform role types. */
enum class RoleType {
    DEV, ADMIN, READER, WRITER;

    companion object {
        /** Returns the [RoleType] matching [value] (case-insensitive), or null if unknown. */
        fun fromString(value: String): RoleType? =
            entries.find { it.name.equals(value, ignoreCase = true) }
    }
}