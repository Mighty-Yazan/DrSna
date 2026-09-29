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
     * SHA-256 hash of the raw refresh token.
     *
     * The database NEVER stores the raw token — only this one-way hash.
     *
     * Lookup flow:
     *   1. Browser sends raw token via HttpOnly cookie.
     *   2. Server hashes it with SHA-256.
     *   3. Server queries the DB for this hash.
     *
     * Even if an attacker reads this column, they cannot submit it to
     * /api/auth/refresh — the server would hash it again, producing a
     * completely different value that matches nothing in the DB.
     */
    @Column(
        name = "token_hash",
        nullable = false,
        unique = true
    )
    var tokenHash: String,

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
     *
     * We keep the refresh session for 7 days, while the access token
     * itself will be only 1 minute.
     */
    @Column(
        nullable = false
    )
    var expiryDate: Instant,

    /*
     * Identifies ONE browser/device session.
     *
     * Example:
     *
     * Laptop  -> sessionId = A
     * Phone   -> sessionId = B
     * Tablet  -> sessionId = C
     *
     * Therefore logging in on one device does not destroy the
     * sessions of the other devices.
     */
    @Column(
        name = "session_id",
        nullable = false,
        unique = true
    )
    var sessionId: UUID = UUID.randomUUID(),

    /*
     * Version of the refresh session.
     *
     * Initial login:
     *
     * tokenVersion = 0
     *
     * First refresh:
     *
     * tokenVersion = 1
     *
     * Second refresh:
     *
     * tokenVersion = 2
     *
     * The access token contains the current version.
     *
     * When refresh happens, the old refresh token row is deleted
     * and the new row receives tokenVersion + 1.
     *
     * Therefore the old access token immediately becomes INVALID.
     */
    @Column(
        name = "token_version",
        nullable = false,
        columnDefinition = "BIGINT DEFAULT 0 NOT NULL"
    )
    var tokenVersion: Long = 0,

    /*
     * Snapshot of User.securityVersion at the time this session
     * was created.
     *
     * Example:
     *
     * securityVersion = 0
     *
     * User changes password:
     *
     * User.securityVersion = 1
     *
     * Old refresh sessions still contain:
     *
     * securityVersion = 0
     *
     * Therefore they are immediately invalid.
     */
    @Column(
        name = "security_version",
        nullable = false,
        columnDefinition = "BIGINT DEFAULT 0 NOT NULL"
    )
    var securityVersion: Long = 0
) {

    /*
     * Returns true when the refresh session has expired.
     */
    fun isExpired(): Boolean {
        return !Instant.now().isBefore(expiryDate)
    }
}