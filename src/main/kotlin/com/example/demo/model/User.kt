package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "full_name", nullable = false, length = 50)
    var fullName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var city: City = City.AMMAN,

    @Column(nullable = false, unique = true, length = 50)
    var email: String = "",

    @Column(nullable = false)
    var password: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var role: Role = Role.PATIENT,

    @Column(name = "clinic_license_number", unique = true, length = 50)
    var clinicLicenseNumber: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)