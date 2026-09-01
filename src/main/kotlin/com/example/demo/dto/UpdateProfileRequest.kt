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

    val city: City,

    val password: String? = null,

    val confirmPassword: String? = null
)