package com.example.demo.dto

import jakarta.validation.constraints.NotNull
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

data class CreateScheduleRequest(
    @field:NotNull(message = "Doctor ID is required")
    val doctorId: UUID?,
    @field:NotNull(message = "Day of week is required")
    val dayOfWeek: DayOfWeek?,
    @field:NotNull(message = "Start time is required")
    val startTime: LocalTime?,
    @field:NotNull(message = "End time is required")
    val endTime: LocalTime?,
    val workingHoursDoctor: String?
)
