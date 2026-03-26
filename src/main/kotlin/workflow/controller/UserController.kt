package org.workflow.controller

import org.workflow.dto.UserCreateRequest
import org.workflow.dto.UserResponse
import org.workflow.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserController(private val userService: UserService) {

    @PostMapping("/register")
    fun registerUser(@RequestBody request: UserCreateRequest): ResponseEntity<UserResponse> {
        val response = userService.createUser(request)
        return ResponseEntity.ok(response)
    }
}