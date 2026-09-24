
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

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(refreshToken, REFRESH_COOKIE_MAX_AGE).toString())
            .body(loginResponse)
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = "refreshToken", required = false) refreshToken: String?
    ): ResponseEntity<LoginResponse> {
        val (loginResponse, newRefreshToken) = authService.refresh(refreshToken)

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie(newRefreshToken, REFRESH_COOKIE_MAX_AGE).toString())
            .body(loginResponse)
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
        val userEmail = jwt.subject ?: throw IllegalStateException("Invalid JWT subject")
        val (profileResponse, newRefreshToken) = authService.updateProfile(userEmail, request)

        val responseBuilder = ResponseEntity.ok()
        if (newRefreshToken != null) {
            responseBuilder.header(HttpHeaders.SET_COOKIE, refreshCookie(newRefreshToken, REFRESH_COOKIE_MAX_AGE).toString())
        }
        return responseBuilder.body(profileResponse)
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

        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshCookie("", 0).toString())
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

    @PutMapping("/change-password")
    @PreAuthorize("hasRole('ADMIN')")
    fun changePassword(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<MessageResponse> {
        val email = jwt.subject ?: throw IllegalStateException("Invalid JWT subject")
        val response = authService.changePassword(email, request)
        return ResponseEntity.ok(response)
    }

    private companion object {
        const val REFRESH_COOKIE_MAX_AGE = 7L * 24 * 60 * 60 // 7 days in seconds
    }
    /**
     * Single source of truth for the refresh-token cookie.
     * Login, refresh and logout MUST use identical Path (and other attributes):
     * the browser identifies a cookie by name + domain + path, so a mismatch
     * leaves two "refreshToken" cookies behind and the stale one gets sent.
     */
    private fun refreshCookie(value: String, maxAgeSeconds: Long): ResponseCookie =
        ResponseCookie.from("refreshToken", value)
            .httpOnly(true)
            .secure(false) // Set to true in production over HTTPS
            .path("/api/auth")
            .maxAge(maxAgeSeconds)
            .sameSite("Strict")
            .build()

}
 
