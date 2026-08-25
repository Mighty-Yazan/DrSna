package com.example.demo.model

import jakarta.persistence.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Entity
@Table(name = "schedules")
class Schedule(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    var type: ScheduleType = ScheduleType.CLINIC_HOURS,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id")
    val clinic: Clinic? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    val doctor: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    var dayOfWeek: DayOfWeek? = null,

    @Column(name = "start_time")
    var startTime: LocalTime? = null,

    @Column(name = "end_time")
    var endTime: LocalTime? = null,

    @Column(name = "specific_date")
    var specificDate: LocalDate? = null,

    @Column(name = "reason")
    var reason: String? = null
)