package com.example.drsna.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService {

    // A 256-bit secret key for signing tokens. (In production, move this to application.yml)
    private val secretKeyString = "DrSnaClinicSuperSecretKeyForJwtTokens2026!!"
    private val key: SecretKey = Keys.hmacShaKeyFor(secretKeyString.toByteArray())
    private val jwtExpirationMs = 86400000 // 24 hours in milliseconds

    // Extracts the username (email) from the token payload
    fun extractUsername(token: String): String? {
        return extractAllClaims(token).subject
    }

    // Creates a new token for a user
    fun generateToken(userDetails: UserDetails): String {
        return Jwts.builder()
            .subject(userDetails.username)
            .issuedAt(Date(System.currentTimeMillis()))
            .expiration(Date(System.currentTimeMillis() + jwtExpirationMs))
            .signWith(key)
            .compact()
    }

    // Validates that the token belongs to the user and isn't expired
    fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return (username == userDetails.username) && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        return extractAllClaims(token).expiration.before(Date())
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
    }
}