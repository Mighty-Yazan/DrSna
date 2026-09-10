package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: java.util.UUID? = null,

    @Column(nullable = false, unique = true)
    var token: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @Column(nullable = false)
    var expiryDate: Instant
) {
    fun isExpired(): Boolean = Instant.now().isAfter(expiryDate)
}
