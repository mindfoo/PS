package org.workflow.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.workflow.dto.UserCreateRequest
import org.workflow.dto.UserResponse
import org.workflow.service.utils.UserError
import org.workflow.service.UserService
import org.workflow.utils.Failure
import org.workflow.utils.Problem
import org.workflow.utils.Success
import org.workflow.utils.Uris

@RestController
@Tag(name = "Users", description = "Legacy user management endpoints")
/** Legacy user controller kept for backwards compatibility. Prefer /api/auth/register. */
class UserController(private val userService: UserService) {

    @PostMapping(Uris.Users.REGISTER)
    @Operation(
        summary = "Legacy register endpoint",
        description = "Prefer /api/auth/register for the full JWT auth flow"
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "User created",
            content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "409", description = "Username already taken",
            content = [Content(mediaType = Problem.MEDIA_TYPE)]),
        ApiResponse(responseCode = "400", description = "Role not found",
            content = [Content(mediaType = Problem.MEDIA_TYPE)])
    )
    fun registerUser(@RequestBody request: UserCreateRequest): ResponseEntity<Any> =
        when (val result = userService.createUser(request)) {
            is Success -> ResponseEntity.status(HttpStatus.CREATED).body(result.value)
            is Failure -> when (result.value) {
                UserError.UsernameAlreadyTaken -> Problem.response(409, Problem.usernameAlreadyTaken)
                UserError.RoleNotFound -> Problem.response(400, Problem.roleNotFound)
            }
        }
}