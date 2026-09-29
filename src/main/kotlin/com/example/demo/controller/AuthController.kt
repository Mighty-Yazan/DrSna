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
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    // =========================================================================
    // REGISTER PATIENT
    // =========================================================================

    @PostMapping("/register/user")
    fun registerUser(
        @Valid @RequestBody request: UserRegisterRequest
    ): ResponseEntity<AuthResponse> {

        val response =
            authService.registerUser(request)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response)
    }

    // =========================================================================
    // REGISTER CLINIC
    // =========================================================================

    @PostMapping("/register/clinic")
    fun registerClinic(
        @Valid @RequestBody request: ClinicRegisterRequest
    ): ResponseEntity<AuthResponse> {

        val response =
            authService.registerClinic(request)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response)
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<LoginResponse> {

        val (loginResponse, refreshToken) =
            authService.login(request)

        return ResponseEntity
            .status(HttpStatus.OK)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie(
                    refreshToken,
                    REFRESH_COOKIE_MAX_AGE
                ).toString()
            )
            .body(loginResponse)
    }

    // =========================================================================
    // REFRESH
    // =========================================================================

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(
            name = "refreshToken",
            required = false
        )
        refreshToken: String?
    ): ResponseEntity<LoginResponse> {

        val (loginResponse, newRefreshToken) =
            authService.refresh(refreshToken)

        return ResponseEntity
            .status(HttpStatus.OK)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie(
                    newRefreshToken,
                    REFRESH_COOKIE_MAX_AGE
                ).toString()
            )
            .body(loginResponse)
    }

    // =========================================================================
    // GET CURRENT USER PROFILE
    // =========================================================================

    @GetMapping("/me")
    fun getProfile(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<UserProfileResponse> {

        val userEmail =
            jwt.subject
                ?: throw IllegalStateException(
                    "Invalid JWT subject"
                )

        val profile =
            authService.getProfile(userEmail)

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(profile)
    }

    // =========================================================================
    // UPDATE PATIENT / DOCTOR PROFILE
    // =========================================================================

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('PATIENT','DOCTOR')")
    fun updateProfile(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<ProfileUpdateResponse> {

        val userEmail =
            jwt.subject
                ?: throw IllegalStateException(
                    "Invalid JWT subject"
                )

        val updated =
            authService.updateProfile(
                userEmail,
                request
            )

        /*
         * Build the response without using generic type arguments
         * on ResponseEntity.ok(), because Spring 7's static ok()
         * does not accept them.
         */
        val responseBuilder =
            ResponseEntity
                .status(HttpStatus.OK)

        /*
         * A new refresh token exists only when:
         * - password changed
         * - email changed
         */
        if (!updated.refreshToken.isNullOrBlank()) {

            responseBuilder.header(
                HttpHeaders.SET_COOKIE,
                refreshCookie(
                    updated.refreshToken,
                    REFRESH_COOKIE_MAX_AGE
                ).toString()
            )
        }

        return responseBuilder.body(updated)
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<MessageResponse> {

        val jti =
            jwt.id

        val expiresAt =
            jwt.expiresAt

        val sessionId =
            jwt.getClaimAsString("sessionId")
                ?.let {
                    runCatching {
                        UUID.fromString(it)
                    }.getOrNull()
                }

        if (
            jti != null &&
            expiresAt != null
        ) {
            authService.logout(
                jti,
                expiresAt.epochSecond,
                sessionId
            )
        }

        /*
         * Remove the refresh cookie from the browser.
         */
        return ResponseEntity
            .status(HttpStatus.OK)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie(
                    "",
                    0
                ).toString()
            )
            .body(
                MessageResponse(
                    "Logged out successfully"
                )
            )
    }

    // =========================================================================
    // REGISTER ADMIN
    // =========================================================================

    @PostMapping("/register/admin")
    @PreAuthorize("hasRole('ADMIN')")
    fun registerAdmin(
        @Valid @RequestBody request: AdminRegisterRequest
    ): ResponseEntity<AuthResponse> {

        val response =
            authService.registerAdmin(request)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response)
    }

    // =========================================================================
    // CHANGE PASSWORD
    // =========================================================================

    @PutMapping("/change-password")
    @PreAuthorize("hasRole('ADMIN')")
    fun changePassword(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<PasswordChangeResponse> {

        val email =
            jwt.subject
                ?: throw IllegalStateException(
                    "Invalid JWT subject"
                )

        val response =
            authService.changePassword(
                email,
                request
            )

        /*
         * changePassword() creates a completely new session
         * after revoking every previous session.
         */
        return ResponseEntity
            .status(HttpStatus.OK)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookie(
                    response.refreshToken,
                    REFRESH_COOKIE_MAX_AGE
                ).toString()
            )
            .body(response)
    }

    // =========================================================================
    // REFRESH TOKEN COOKIE
    // =========================================================================

    private fun refreshCookie(
        value: String,
        maxAgeSeconds: Long
    ): ResponseCookie {

        return ResponseCookie
            .from(
                "refreshToken",
                value
            )
            .httpOnly(true)
            .secure(false)
            .path("/api/auth")
            .maxAge(maxAgeSeconds)
            .sameSite("Strict")
            .build()
    }

    private companion object {

        /*
         * Refresh cookie lifetime:
         * 7 days.
         */
        const val REFRESH_COOKIE_MAX_AGE =
            7L * 24 * 60 * 60
    }
}