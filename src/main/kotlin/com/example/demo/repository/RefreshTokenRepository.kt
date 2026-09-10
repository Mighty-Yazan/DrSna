package com.example.demo.repository

import com.example.demo.model.RefreshToken
import com.example.demo.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, java.util.UUID> {
    fun findByToken(token: String): RefreshToken?
    
    @Modifying
    fun deleteByUser(user: User): Int
}
