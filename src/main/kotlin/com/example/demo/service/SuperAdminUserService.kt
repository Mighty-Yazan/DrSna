package com.example.demo.service

import com.example.demo.dto.UserManagementDto
import com.example.demo.model.Role
import com.example.demo.model.User
import com.example.demo.repository.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SuperAdminUserService(
    private val userRepository: UserRepository
) {

    // جلب وتصفية المستخدمين بناءً على الدور وكلمة البحث
    fun getUsers(role: Role?, query: String?): List<UserManagementDto> {
        val users = when {
            role != null && !query.isNullOrBlank() -> {
                userRepository.searchUsersByRoleAndQuery(role, query.trim())
            }
            role != null -> {
                userRepository.findAllByRole(role)
            }
            !query.isNullOrBlank() -> {
                userRepository.searchAllUsersByQuery(query.trim())
            }
            else -> {
                userRepository.findAll()
            }
        }

        return users.map { mapToDto(it) }
    }

    // تعليق/حظر أو إعادة تفعيل حساب مستخدم (Toggle / Explicit Active status)
    fun toggleUserActiveStatus(userId: UUID, isActive: Boolean): UserManagementDto {
        val user = userRepository.findById(userId)
            .orElseThrow { NoSuchElementException("User not found with id: $userId") }

        user.isActive = isActive
        val updatedUser = userRepository.save(user)

        return mapToDto(updatedUser)
    }

    private fun mapToDto(user: User): UserManagementDto {
        return UserManagementDto(
            id = user.id ?: UUID.randomUUID(),
            fullName = user.fullName,
            email = user.email,
            role = user.role,
            isActive = user.isActive
        )
    }
}