package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "refresh_tokens"
)
class RefreshToken(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    /*
     * One-way SHA-256 hash of the raw refresh token.
     *
     * The raw token is NEVER stored in the database.
     */
    @Column(
        name = "token_hash",
        nullable = false,
        unique = true,
        length = 64
    )
    var tokenHash: String,

    /*
     * Temporary in-memory value used only so the caller can send the
     * newly generated raw token to the browser cookie.
     *
     * This field is NOT persisted to the database.
     */
    @Transient
    var rawToken: String? = null,

    /*
     * User who owns this refresh session.
     */
    @ManyToOne(
        fetch = FetchType.LAZY
    )
    @JoinColumn(
        name = "user_id",
        nullable = false
    )
    var user: User,

    /*
     * Refresh-token expiration date.
     */
    @Column(
        nullable = false
    )
    var expiryDate: Instant,

    /*
     * Identifies ONE browser/device session.
     */
    @Column(
        name = "session_id",
        nullable = false,
        unique = true
    )
    var sessionId: UUID = UUID.randomUUID(),

    /*
     * Version of the refresh session.
     */
    @Column(
        name = "token_version",
        nullable = false,
        columnDefinition = "BIGINT DEFAULT 0 NOT NULL"
    )
    var tokenVersion: Long = 0,

    /*
     * Snapshot of User.securityVersion when this session was created.
     */
    @Column(
        name = "security_version",
        nullable = false,
        columnDefinition = "BIGINT DEFAULT 0 NOT NULL"
    )
    var securityVersion: Long = 0
) {

    fun isExpired(): Boolean {
        return !Instant.now().isBefore(expiryDate)
    }
}
