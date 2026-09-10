package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
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
        val (loginResponse, refreshToken) = authService.login(request)
        
        val cookie = ResponseCookie.from("refreshToken", refreshToken)
            .httpOnly(true)
            .secure(false) // Set to true in production
            .path("/api/auth")
            .maxAge(7 * 24 * 60 * 60)
            .sameSite("Strict")
            .build()
            
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(loginResponse)
    }

    @PostMapping("/refresh")
    fun refresh(@CookieValue(name = "refreshToken", required = true) refreshToken: String): ResponseEntity<LoginResponse> {
        val response = authService.refresh(refreshToken)
        return ResponseEntity.ok(response)
    }

    // Protected: Requires a valid Bearer Token
    @GetMapping("/me")
    fun getProfile(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<UserProfileResponse> {
        val userEmail = jwt.subject
        val profile = authService.getProfile(userEmail!!)
        return ResponseEntity.ok(profile)
    }

    // Edit Info: available to Patient and Doctor accounts only
    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    fun updateProfile(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<UserProfileResponse> {
        val userEmail = jwt.subject
        val updated = authService.updateProfile(userEmail!!, request)
        return ResponseEntity.ok(updated)
    }

    // Protected: Blacklists the current token's JTI so it cannot be reused
    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal jwt: Jwt,
        @CookieValue(name = "refreshToken", required = false) refreshToken: String?
    ): ResponseEntity<MessageResponse> {
        val jti = jwt.id
        val expiresAt = jwt.expiresAt
        if (jti != null && expiresAt != null) {
            authService.logout(jti, expiresAt.epochSecond, refreshToken)
        }
        
        val clearCookie = ResponseCookie.from("refreshToken", "")
            .httpOnly(true)
            .secure(false) // Set to true in production
            .path("/api/auth")
            .maxAge(0)
            .sameSite("Strict")
            .build()
            
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
            .body(MessageResponse("Logged out successfully"))
    }

    @PostMapping("/register/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun registerAdmin(
        @Valid @RequestBody request: AdminRegisterRequest
    ): ResponseEntity<AuthResponse> {
        val response = authService.registerAdmin(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

}