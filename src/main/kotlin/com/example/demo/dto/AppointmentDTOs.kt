package com.example.demo.dto

import com.example.demo.model.AppointmentStatus
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

data class AppointmentSummaryResponse(
    val appointmentId: UUID,
    val clinicId: UUID,
    val clinicName: String,
    val doctorId: UUID,
    val doctorName: String,
    val patientId: UUID?,
    val patientName: String?,
    val serviceId: UUID?,
    val serviceName: String?,
    val scheduleId: Long?,
    val appointmentAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val status: AppointmentStatus
)

data class AppointmentListResponse(
    val scope: String,
    val count: Int,
    val appointments: List<AppointmentSummaryResponse>
)

data class CreateWalkInAppointmentRequest(
    val doctorId: UUID?,
    val appointmentDate: java.time.LocalDate?,
    val appointmentTime: java.time.LocalTime?
)

// ============================================================
// DOHA — Appointment Form Booking DTOs
// POST /api/appointments/book
// ============================================================

/**
 * Request body sent from the Frontend Appointment Form.
 * Fields: Name, Age, Service, Time, Payment Method.
 */
data class BookAppointmentRequest(

    @field:NotBlank(message = "Patient name is required")
    @field:Size(max = 150, message = "Patient name must not exceed 150 characters")
    val patientName: String,

    @field:NotNull(message = "Patient age is required")
    @field:Min(value = 0, message = "Patient age must be a positive number")
    val patientAge: Int,

    @field:NotNull(message = "Service ID is required")
    val serviceId: UUID,

    @field:NotNull(message = "Appointment date and time are required")
    val appointmentAt: LocalDateTime,

    @field:NotBlank(message = "Payment method is required")
    @field:Size(max = 50, message = "Payment method must not exceed 50 characters")
    val paymentMethod: String
)

/**
 * Response returned to the Frontend after a successful booking.
 */
data class BookAppointmentResponse(
    val appointmentId: UUID,
    val message: String,
    val patientName: String,
    val patientAge: Int,
    val serviceName: String,
    val appointmentAt: OffsetDateTime,
    val paymentMethod: String,
    val status: AppointmentStatus
)
