package com.example.demo.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class SpecialtyRequest(
    @field:NotBlank(message = "Specialty name is required")
    val name: String,

    @field:Min(value = 1, message = "Service duration must be at least 1 minute")
    val durationMinutes: Int = 60
)

data class SpecialtyResponse(
    val id: UUID,
    val name: String,
    val durationMinutes: Int
)

data class UpdateSpecialtyDurationRequest(
    @field:Min(value = 1, message = "Service duration must be at least 1 minute")
    val durationMinutes: Int
)

data class ReviewReplyRequest(val reply: String)