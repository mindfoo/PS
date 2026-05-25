package org.workflow.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.workflow.dto.RoleSummaryResponse
import org.workflow.dto.UserAdminResponse
import org.workflow.dto.UserRoleUpdateRequest
import org.workflow.service.utils.UserError
import org.workflow.service.UserService
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris
import java.util.UUID

/** Exposes user management and role administration endpoints (admin only). */
@RestController
@Tag(name = "Users", description = "User management endpoints — restricted to the user:manage authority")
class UserController(private val userService: UserService) {

    @GetMapping(Uris.Users.BASE)
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "List users", description = "Returns all users with their role and effective permission set")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Users returned",
            content = [Content(schema = Schema(implementation = UserAdminResponse::class))]),
        ApiResponse(responseCode = "401", description = "Not authenticated",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions — user:manage required",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun listUsers(): ResponseEntity<List<UserAdminResponse>> =
        ResponseEntity.ok(userService.listUsers())

    @GetMapping(Uris.Users.ROLES)
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "List roles", description = "Returns the role catalogue with associated permission slugs")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Roles returned",
            content = [Content(schema = Schema(implementation = RoleSummaryResponse::class))]),
        ApiResponse(responseCode = "401", description = "Not authenticated",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions — user:manage required",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun listRoles(): ResponseEntity<List<RoleSummaryResponse>> =
        ResponseEntity.ok(userService.listRoles())

    @PatchMapping(Uris.Users.UPDATE_ROLE)
    @PreAuthorize("hasAuthority('user:manage')")
    @Operation(summary = "Update user role", description = "Assigns a different role to an existing user")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Role updated",
            content = [Content(schema = Schema(implementation = UserAdminResponse::class))]),
        ApiResponse(responseCode = "400", description = "Role not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "401", description = "Not authenticated",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions — user:manage required",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "404", description = "User not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun updateUserRole(
        @PathVariable("id") userId: UUID,
        @RequestBody request: UserRoleUpdateRequest
    ): ResponseEntity<Any> =
        when (val result = userService.updateUserRole(userId, request)) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure -> when (result.value) {
                UserError.RoleNotFound         -> Problem.response(400, Problem.roleNotFound)
                UserError.UserNotFound         -> Problem.response(404, Problem.userNotFound)
                UserError.UsernameAlreadyTaken -> Problem.response(409, Problem.usernameAlreadyTaken)
            }
        }
}
