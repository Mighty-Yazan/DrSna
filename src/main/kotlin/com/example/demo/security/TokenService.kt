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
            .expiresAt(now.plus(15, ChronoUnit.MINUTES)) // 15 mins
            .subject(user.email)
            .id(UUID.randomUUID().toString())                // JTI for blacklist support
            .claim("userId", user.id.toString())             // UUID as string
            .claim("roles", listOf("ROLE_${user.role.name}"))
            .build()

        val parameters = JwtEncoderParameters.from(JwsHeader.with { "RS256" }.build(), claims)
        return encoder.encode(parameters).tokenValue
    }

    @Transactional
    fun generateRefreshToken(user: User): String {
        val token = UUID.randomUUID().toString()
        val refreshToken = RefreshToken(
            token = token,
            user = user,
            expiryDate = Instant.now().plus(7, ChronoUnit.DAYS)
        )
        refreshTokenRepository.save(refreshToken)
        return token
    }

    @Transactional(readOnly = true)
    fun validateRefreshToken(token: String): RefreshToken? {
        val refreshToken = refreshTokenRepository.findByToken(token) ?: return null
        if (refreshToken.isExpired()) {
            return null
        }
        return refreshToken
    }
    
    @Transactional
    fun deleteByToken(token: String) {
        refreshTokenRepository.findByToken(token)?.let {
            refreshTokenRepository.delete(it)
        }
    }
}
