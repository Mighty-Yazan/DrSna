package com.example.demo.dto

import com.example.demo.model.City
import com.example.demo.model.Role
import java.util.UUID

data class ProfileResponse(
    val userId: UUID?,
    val fullName: String,
    val email: String,
    val city: City,
    val role: Role,
    val bio: String?,
    val specialty: String?
)