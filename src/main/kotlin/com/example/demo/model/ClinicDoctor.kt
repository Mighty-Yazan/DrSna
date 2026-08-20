package com.example.demo.model

import jakarta.persistence.*
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class ClinicDoctorId(
    @Column(name = "doctor_user_id")
    var doctorUserId: UUID = UUID(0, 0),

    @Column(name = "clinic_user_id")
    var clinicUserId: UUID = UUID(0, 0)
) : Serializable

@Entity
@Table(name = "clinic_doctors")
class ClinicDoctor(
    @EmbeddedId
    var id: ClinicDoctorId = ClinicDoctorId(),

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("doctorUserId")
    @JoinColumn(name = "doctor_user_id")
    var doctor: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("clinicUserId")
    @JoinColumn(name = "clinic_user_id")
    var clinic: User? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)