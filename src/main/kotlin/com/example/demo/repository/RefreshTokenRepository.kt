package com.example.demo.repository

import com.example.demo.model.RefreshToken
import com.example.demo.model.User
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    /** Looks up a token by its stored HMAC digest. */
    fun findByTokenHash(tokenHash: String): RefreshToken?

    /** Locked lookup used by refresh-token rotation/reuse detection. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
    fun findByTokenHashForUpdate(@Param("tokenHash") tokenHash: String): RefreshToken?

    /** Finds the active token for one browser/device session. */
    @Query("select r from RefreshToken r where r.sessionId = :sessionId and r.revokedAt is null")
    fun findActiveBySessionId(@Param("sessionId") sessionId: UUID): RefreshToken?

    /** Revokes every token in one refresh-token family. */
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :revokedAt where r.sessionId = :sessionId and r.revokedAt is null")
    fun revokeActiveBySessionId(
        @Param("sessionId") sessionId: UUID,
        @Param("revokedAt") revokedAt: Instant
    ): Int

    /** Revokes every token belonging to a user, retaining hashes for replay detection. */
    @Modifying
    @Query("update RefreshToken r set r.revokedAt = :revokedAt where r.user = :user and r.revokedAt is null")
    fun revokeActiveByUser(
        @Param("user") user: User,
        @Param("revokedAt") revokedAt: Instant
    ): Int

    /** Removes expired historical/current tokens after their reuse window ends. */
    fun deleteByExpiryDateBefore(now: Instant): Long

    /** Legacy rows from the pre-HMAC implementation are identified by UUID shape. */
    @Query("select r from RefreshToken r")
    fun findAllForMigration(): List<RefreshToken>
}