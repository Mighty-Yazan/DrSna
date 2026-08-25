package com.example.demo.dto

import com.example.demo.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

data class ScheduleResponseDto(
    val id: Long?,
    val type: ScheduleType,
    val dayOfWeek: DayOfWeek?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val specificDate: LocalDate?,
    val reason: String?,
    val doctorId: String?
)

data class SaveClinicHoursRequest(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime
)

data class SaveDoctorScheduleRequest(
    val doctorId: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime
)

data class SaveHolidayRequest(
    val specificDate: LocalDate,
    val reason: String?,
    val doctorId: String? = null
)