package com.example.demo.model

import jakarta.persistence.*
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "schedules")
class Schedule(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @Column(name = "clinic_id", nullable = false)
    val clinicId: UUID,

    @Column(name = "doctor_user_id", nullable = false)
    val doctorUserId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 20)
    var dayOfWeek: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY,

    @Column(name = "start_time", nullable = false)
    var startTime: LocalTime = LocalTime.MIN,

    @Column(name = "end_time", nullable = false)
    var endTime: LocalTime = LocalTime.MIN,

    @Column(name = "working_hours_doctor", length = 50)
    var workingHoursDoctor: String? = null
)
