package com.example.demo.dto

import com.example.demo.model.City
import com.example.demo.model.Role
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

// ── Registration ────────────────────────────────────────────────────

data class UserRegisterRequest(
    @field:NotBlank(message = "Full name is required")
    @field:Size(min = 2, message = "Full name must be at least 2 characters")
    val fullName: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be a valid email address")
    val email: String,

    @field:NotNull(message = "City is required")
    val city: City,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 12, message = "Password must be between 8 and 12 characters")
    @field:Pattern(
        regexp = "^(?=.*[0-9])(?=.*[^a-zA-Z0-9]).+$",
        message = "Password must contain at least one number and one special character"
    )
    val password: String,

    @field:NotBlank(message = "Confirm password is required")
    val confirmPassword: String
)

data class ClinicRegisterRequest(
    @field:NotBlank(message = "Clinic name is required")
    @field:Size(min = 2, message = "Clinic name must be at least 2 characters")
    val clinicName: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be a valid email address")
    val email: String,

    @field:NotNull(message = "City is required")
    val city: City,

    @field:NotBlank(message = "Clinic license number is required")
    val clinicLicenseNumber: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 12, message = "Password must be between 8 and 12 characters")
    @field:Pattern(
        regexp = "^(?=.*[0-9])(?=.*[^a-zA-Z0-9]).+$",
        message = "Password must contain at least one number and one special character"
    )
    val password: String,

    @field:NotBlank(message = "Confirm password is required")
    val confirmPassword: String
)

// ── Auth Responses ──────────────────────────────────────────────────

data class AuthResponse(
    val message: String,
    val userId: UUID?,
    val email: String,
    val role: Role
)

// ── Login ────────────────────────────────────────────────────────────

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be a valid email address")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)

data class LoginResponse(
    val token: String,
    val tokenType: String = "Bearer",
    val userId: UUID?,
    val email: String,
    val role: Role
)

// ── Profile ──────────────────────────────────────────────────────────

data class UserProfileResponse(
    val userId: UUID?,
    val fullName: String,
    val email: String,
    val city: City,
    val role: Role,
    val clinicLicenseNumber: String? = null
)

// ── General ──────────────────────────────────────────────────────────

data class MessageResponse(
    val message: String
)