package com.example.demo.repository

import com.example.demo.model.Role
import com.example.demo.model.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun existsByClinicLicenseNumber(clinicLicenseNumber: String): Boolean
    fun existsByRole(role: Role): Boolean
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstById(id: UUID): User?
}