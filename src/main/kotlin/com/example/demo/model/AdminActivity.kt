package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "admin_activities")
class AdminActivity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "activity_id", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "admin_user_id")
    var adminUserId: UUID? = null,

    @Column(name = "action", nullable = false, length = 50)
    var action: String = "",

    @Column(name = "description", nullable = false, length = 500)
    var description: String = "",

    @Column(name = "clinic_id")
    var clinicId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
)