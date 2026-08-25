package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "reviews", uniqueConstraints = [UniqueConstraint(name = "uk_review_appointment", columnNames = ["appointment_id"])])
class Review(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "review_id", updatable = false, nullable = false)
    var id: UUID? = null,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    var appointment: Appointment? = null,

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "patient_user_id", nullable = false)
    var patient: User? = null,

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "clinic_id", nullable = false)
    var clinic: Clinic? = null,

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "doctor_user_id", nullable = false)
    var doctor: User? = null,

    @Column(nullable = false)
    var rating: Int = 5,

    @Column(columnDefinition = "TEXT")
    var comment: String? = null,

    @Column(columnDefinition = "TEXT")
    var reply: String? = null,

    @Column(name = "reply_at")
    var replyAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)