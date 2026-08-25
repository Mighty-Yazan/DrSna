package com.example.demo.dto

import jakarta.validation.constraints.NotBlank
import java.util.UUID

data class SpecialtyRequest(
    @field:NotBlank(message = "Specialty name is required")
    val name: String
)

data class SpecialtyResponse(
    val id: UUID,
    val name: String
)

data class ReviewReplyRequest(val reply: String)