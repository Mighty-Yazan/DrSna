package com.example.demo.dto

import java.time.OffsetDateTime
import java.util.UUID

data class AppointmentSummaryResponse(
    val appointmentId: UUID,
    val clinicId: UUID,
    val clinicName: String,
    val doctorId: UUID,
    val doctorName: String,
    val patientId: UUID,
    val patientName: String,
    val serviceId: UUID,
    val serviceName: String,
    val scheduleId: Long?,
    val appointmentAt: OffsetDateTime,
    val createdAt: OffsetDateTime
)

data class AppointmentListResponse(
    val scope: String,
    val count: Int,
    val appointments: List<AppointmentSummaryResponse>
)
