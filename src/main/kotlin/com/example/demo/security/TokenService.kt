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
    fun rotateRefreshToken(oldTokenString: String): Pair<String, RefreshToken>? {
        val oldToken = refreshTokenRepository.findByToken(oldTokenString) ?: return null

        if (oldToken.isExpired()) {
            refreshTokenRepository.delete(oldToken)
            return null
        }

        val user = oldToken.user
        // Delete the consumed token (RTR single-use principle)
        refreshTokenRepository.delete(oldToken)

        //  brand new refresh token
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
    fun deleteByToken(token: String) {//on logout
        refreshTokenRepository.findByToken(token)?.let {
            refreshTokenRepository.delete(it)
        }
    }
}