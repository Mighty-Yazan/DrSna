package com.example.demo.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "appointments",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_appointments_booking_key",
            columnNames = ["booking_key"]
        )
    ]
)
class Appointment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "appointment_id", updatable = false, nullable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_user_id", nullable = false)
    var clinic: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_user_id", nullable = false)
    var doctor: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_user_id", nullable = true)
    var patient: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    var service: Services? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    var schedule: Schedule? = null,

    @Column(name = "appointment_date", nullable = false)
    var appointmentDate: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: AppointmentStatus = AppointmentStatus.PENDING,

    /*
     * This value uniquely reserves an active appointment slot.
     *
     * Example:
     * doctorId + appointmentTime
     *
     * When an appointment is cancelled, bookingKey becomes null.
     * PostgreSQL allows multiple NULL values in a UNIQUE column,
     * therefore the cancelled appointment remains as history while
     * the same time slot can be booked again.
     */
    @Column(name = "booking_key", unique = true, length = 100)
    var bookingKey: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)