package com.example.demo.repository

import com.example.demo.model.RefreshToken
import com.example.demo.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findByToken(token: String): RefreshToken?

    // Pure Spring Data JPA derived delete methods (No @Query needed)
    fun deleteByUser(user: User)

    fun deleteByExpiryDateBefore(now: Instant): Long
}