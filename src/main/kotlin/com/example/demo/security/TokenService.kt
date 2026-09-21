package com.example.demo.security

import com.example.demo.model.RefreshToken
import com.example.demo.model.User
import com.example.demo.repository.RefreshTokenRepository
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class TokenService(
    private val encoder: JwtEncoder,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    fun generateAccessToken(user: User): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plus(60, ChronoUnit.MINUTES))
            .subject(user.email)
            .id(UUID.randomUUID().toString()) // JTI
            .claim("userId", user.id.toString())
            .claim("roles", listOf("ROLE_${user.role.name}"))
            .build()

        val parameters = JwtEncoderParameters.from(JwsHeader.with { "RS256" }.build(), claims)
        return encoder.encode(parameters).tokenValue
    }


    // Generates a new refresh token. Cleans up existing tokens for this user to prevent accumulation.

    @Transactional
    fun generateRefreshToken(user: User): String {
        refreshTokenRepository.deleteByUser(user)//if there is already one

        val token = UUID.randomUUID().toString()
        val refreshToken = RefreshToken(
            token = token,
            user = user,
            expiryDate = Instant.now().plus(7, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(refreshToken)
        return token
    }


    //when hitting refresh Validates the old token, deletes it, and generates a new one.
    @Transactional
    fun rotateRefreshToken(rawToken: String): Pair<String, RefreshToken>? {
        // 1. Find by token using pure JPA method (returns RefreshToken?)
        val existingToken = refreshTokenRepository.findByToken(rawToken) ?: return null

        // 2. Access properties directly on the entity
        if (existingToken.isExpired()) {
            refreshTokenRepository.delete(existingToken)
            return null
        }

        val user = existingToken.user

        // 3. Remove old token using Spring JPA's built-in delete
        refreshTokenRepository.delete(existingToken)

        // 4. Create and save new token using Spring JPA's built-in save
        val newTokenString = UUID.randomUUID().toString()
        val newRefreshToken = RefreshToken(
            token = newTokenString,
            user = user,
            expiryDate = Instant.now().plus(7, ChronoUnit.DAYS)
        )

        val savedToken = refreshTokenRepository.save(newRefreshToken)
        return Pair(newTokenString, savedToken)
    }

    @Transactional
    fun deleteByToken(token: String) {
        val existing = refreshTokenRepository.findByToken(token)
        if (existing != null) {
            refreshTokenRepository.delete(existing)
        }
    }
}