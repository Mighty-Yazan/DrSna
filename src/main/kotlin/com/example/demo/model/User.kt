package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
        name = "user_id",
        updatable = false,
        nullable = false
    )
    var id: UUID? = null,

    @Column(
        name = "full_name",
        nullable = false,
        length = 50
    )
    var fullName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 20
    )
    var city: City = City.AMMAN,

    @Column(
        nullable = false,
        unique = true,
        length = 50
    )
    var email: String = "",

    @Column(
        name = "phone_number",
        length = 20
    )
    var phoneNumber: String? = null,

    @Column(nullable = false)
    var password: String = "",

    @Enumerated(EnumType.STRING)
    @Column(
        nullable = false,
        length = 50
    )
    var role: Role = Role.PATIENT,

    @Column(
        name = "clinic_license_number",
        unique = true,
        length = 50
    )
    var clinicLicenseNumber: String? = null,

    @Column(
        columnDefinition = "TEXT"
    )
    var bio: String? = null,

    @Column(
        length = 100
    )
    var specialty: String? = null,

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    val createdAt: Instant = Instant.now(),

    @Column(
        name = "is_active",
        nullable = false
    )
    var isActive: Boolean = true,

    /**
     * Security version of the user's account.
     *
     * Every access token contains the current value.
     *
     * When a security-sensitive action happens, such as:
     *
     * - password change
     * - administrative password reset
     * - account deactivation
     * - doctor deactivation
     * - clinic deactivation
     *
     * this value is incremented.
     *
     * Any old access token containing the previous value
     * immediately becomes invalid.
     */
    @Column(
        name = "security_version",
        nullable = false,
        columnDefinition = "BIGINT DEFAULT 0 NOT NULL"
    )
    var securityVersion: Long = 0
)