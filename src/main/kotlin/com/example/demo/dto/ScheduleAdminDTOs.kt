package com.example.demo.dto

import java.util.UUID

data class ScheduleRequest(
    val clinicId: UUID,
    val doctorUserId: UUID,
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
    val workingHoursDoctor: String? = null
)