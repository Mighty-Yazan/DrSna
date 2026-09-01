package com.example.demo.dto

import com.example.demo.model.City
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class AddFavoriteDoctorRequest(
    @field:NotNull(message = "Doctor ID is required")
    val doctorId: UUID
)

data class FavoriteDoctorResponse(
    val id: UUID,
    val doctorName: String,
    val clinicName: String?,
    val specialties: String?,
    val city: City
)
