package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    /**
     * HMAC-SHA-256 of the raw refresh token.
     *
     * The raw bearer token is never persisted.
     * The existing database column is kept as `token` for a zero-column-rename
     * migration; the application property is explicitly named `tokenHash`.
     */
    @Column(name = "token", nullable = false, unique = true)
    var tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false)
    var expiryDate: Instant,

    /** One refresh-token family / browser session. */
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID = UUID.randomUUID(),

    @Column(name = "token_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0 NOT NULL")
    var tokenVersion: Long = 0,

    @Column(name = "security_version", nullable = false, columnDefinition = "BIGINT DEFAULT 0 NOT NULL")
    var securityVersion: Long = 0,

    /**
     * Null means this token is the currently active token in the family.
     * A non-null value means the token was rotated/revoked and must never be
     * accepted again.
     */
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
) {

    fun isExpired(): Boolean = !Instant.now().isBefore(expiryDate)

    fun isRevoked(): Boolean = revokedAt != null
}