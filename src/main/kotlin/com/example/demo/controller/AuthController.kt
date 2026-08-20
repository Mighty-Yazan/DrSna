package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register/user")
    fun registerUser(@Valid @RequestBody request: UserRegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.registerUser(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/register/clinic")
    fun registerClinic(@Valid @RequestBody request: ClinicRegisterRequest): ResponseEntity<AuthResponse> {
        val response = authService.registerClinic(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val response = authService.login(request)
        return ResponseEntity.ok(response)
    }

    // Protected: Requires a valid Bearer Token
    @GetMapping("/me")
    fun getProfile(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<UserProfileResponse> {
        val userEmail = jwt.subject
        val profile = authService.getProfile(userEmail!!)
        return ResponseEntity.ok(profile)
    }

    // Protected: Blacklists the current token's JTI so it cannot be reused
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<MessageResponse> {
        val jti = jwt.id
        val expiresAt = jwt.expiresAt
        if (jti != null && expiresAt != null) {
            authService.logout(jti, expiresAt.epochSecond)
        }
        return ResponseEntity.ok(MessageResponse("Logged out successfully"))
    }
}