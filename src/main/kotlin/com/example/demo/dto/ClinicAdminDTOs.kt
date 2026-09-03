package com.example.demo.dto

import com.example.demo.model.City
import com.example.demo.model.Role
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.util.UUID

// ── CLINIC PROFILE DTOs ──────────────────────────────────────────────

data class ClinicProfileRequest(
    @field:NotBlank(message = "Clinic name cannot be blank")
    @field:Size(max = 100, message = "clinic name must not exceed 100 characters")
    val clinicName: String,

    @field:Size(max = 10, message = "Phone number must not exceed 10 characters")
    val phoneNumber: String?,

    val socialLinks: List<String>?,
    @field:Size(max = 255, message = "Detailed address must not exceed 255 characters")
    val detailedAddress: String?,

    @field:Size(max = 255, message = "Working hours must not exceed 255 characters")
    val workingHours: String?,

    @field:DecimalMin(value = "0.0", inclusive = true, message = "Checking fee cannot be negative")
    val checkingFee: BigDecimal?,

    val description: String?,
    val city: City?
)

data class ClinicProfileResponse(
    val id: UUID,
    val userId: UUID,
    val clinicName: String,
    val phoneNumber: String?,
    val socialLinks: List<String>,
    val detailedAddress: String?,
    val workingHours: String?,
    val checkingFee: BigDecimal?,
    val rating: BigDecimal,
    val description: String?,
    val city: City
)



// ── DOCTOR DTOs ──────────────────────────────────────────────────────

data class AddDoctorRequest(
    @field:NotBlank(message = "Doctor full name is required")
    @field:Size(min = 2, message = "Full name must be at least 2 characters")
    val fullName: String,

    @field:NotBlank(message = "Doctor email is required")
    @field:Email(message = "Email must be a valid email address")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 12, message = "Password must be between 8 and 12 characters")
    @field:Pattern(
        regexp = "^(?=.*[0-9])(?=.*[^a-zA-Z0-9]).+$",
        message = "Password must contain at least one number and one special character"
    )
    val password: String,

    val city: City = City.AMMAN,
    val bio: String? = null,
    val specialty: String? = null
)

data class UpdateDoctorRequest(
    @field:NotBlank(message = "Doctor full name is required")
    @field:Size(min = 2, message = "Full name must be at least 2 characters")
    val fullName: String,

    val city: City? = null,
    val bio: String? = null,
    val specialty: String? = null
)

data class DoctorResponse(
    val doctorUserId: UUID,
    val fullName: String,
    val email: String,
    val city: City,
    val role: Role = Role.DOCTOR,
    val isActive: Boolean = true,
    val bio: String? = null,
    val specialty: String? = null
)