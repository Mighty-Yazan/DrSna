package com.example.demo.security

import com.example.demo.model.User
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class TokenService(private val encoder: JwtEncoder) {

    fun generateToken(user: User): String {
        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(now)
            .expiresAt(now.plus(24, ChronoUnit.HOURS))
            .subject(user.email)
            .id(UUID.randomUUID().toString())                // JTI for blacklist support
            .claim("userId", user.id.toString())             // UUID as string
            .claim("role", user.role.name)
            .build()

        val parameters = JwtEncoderParameters.from(JwsHeader.with { "HS256" }.build(), claims)
        return encoder.encode(parameters).tokenValue
    }
}