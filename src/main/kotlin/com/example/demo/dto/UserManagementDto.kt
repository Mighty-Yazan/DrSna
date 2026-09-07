package com.example.demo.dto

import com.example.demo.model.Role
import java.util.UUID

data class UserManagementDto(
    val id: UUID,
    val fullName: String,
    val email: String,
    val role: Role,
    val isActive: Boolean
)