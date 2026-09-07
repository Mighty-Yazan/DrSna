package com.example.demo.dto

import com.example.demo.model.City
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class UpdateProfileRequest(

    @field:NotBlank(message = "Full name is required")
    @field:Size(max = 50, message = "Full name must not exceed 50 characters")
    val fullName: String,

    @field:Email(message = "Invalid email address")
    @field:NotBlank(message = "Email is required")
    val email: String,

    @field:Size(max = 20, message = "Phone number must not exceed 20 characters")
    val phoneNumber: String? = null,

    val city: City,

    @field:NotBlank(message = "Current password is required to save changes")
    val currentPassword: String,

    val newPassword: String? = null,

    val confirmPassword: String? = null
)