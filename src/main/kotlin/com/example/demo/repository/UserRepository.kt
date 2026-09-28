package com.example.demo.repository

import com.example.demo.model.Role
import com.example.demo.model.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmail(email: String): Optional<User>

    /**
     * Locks the user row while a security-sensitive operation is running.
     *
     * Used for:
     * - password changes
     * - account reset
     * - account deactivation
     * - securityVersion changes
     *
     * This prevents concurrent requests from changing the same
     * security state at the same time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    fun findByEmailForUpdate(
        @Param("email") email: String
    ): Optional<User>

    fun existsByEmail(email: String): Boolean

    fun existsByClinicLicenseNumber(
        clinicLicenseNumber: String
    ): Boolean

    fun existsByRole(role: Role): Boolean

    /**
     * Locks the user by ID.
     *
     * Token refresh uses this lock before rotating the session,
     * so security-sensitive session operations are serialized
     * for the same user.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findFirstById(id: UUID): User?
}