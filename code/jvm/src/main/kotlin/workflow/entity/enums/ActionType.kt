package org.workflow.entity.enums

/** Represents the type of action a permission grants on a resource. */
@Suppress("unused")
enum class ActionType {
    READ, WRITE, DELETE, EXECUTE, MANAGE
}