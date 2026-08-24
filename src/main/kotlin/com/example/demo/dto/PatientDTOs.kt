package com.example.demo.dto

import com.example.demo.model.City
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

// -------------------- Patient clinic discovery --------------------

data class ClinicSummaryResponse(
    val clinicId: UUID,
    val clinicUserId: UUID,
    val clinicName: String,
    val city: City,
    val rating: BigDecimal,
    val detailedAddress: String?,
    val phoneNumber: String?,
    val checkingFee: BigDecimal?,
    val workingHours: String?,
    val services: List<String>,
    val available: Boolean? = null
)

data class ClinicDetailsResponse(
    val clinicId: UUID,
    val clinicUserId: UUID,
    val clinicName: String,
    val city: City,
    val rating: BigDecimal,
    val detailedAddress: String?,
    val phoneNumber: String?,
    val socialLinks: String?,
    val checkingFee: BigDecimal?,
    val description: String?,
    val workingHours: String?,
    val services: List<ServiceDetailsResponse>,
    val doctors: List<DoctorDetailsResponse>
)

data class ServiceDetailsResponse(
    val serviceId: UUID,
    val serviceName: String,
    val description: String?
)

data class DoctorDetailsResponse(
    val doctorId: UUID,
    val fullName: String,
    val email: String,
    val schedules: List<ScheduleResponse>
)

data class ScheduleResponse(
    val scheduleId: UUID,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val workingHoursDoctor: String?
)

data class AvailabilitySlotResponse(
    val scheduleId: UUID,
    val doctorId: UUID,
    val doctorName: String,
    val date: LocalDate,
    val time: LocalTime,
    val appointmentAt: OffsetDateTime,
    val available: Boolean
)

// -------------------- Appointment booking --------------------

data class CreateAppointmentRequest(
    @field:NotNull(message = "Clinic ID is required")
    val clinicId: UUID?,

    @field:NotNull(message = "Doctor ID is required")
    val doctorId: UUID?,

    @field:NotNull(message = "Service ID is required")
    val serviceId: UUID?,

    @field:NotNull(message = "Schedule ID is required")
    val scheduleId: UUID?,

    @field:NotNull(message = "Appointment date is required")
    val appointmentDate: LocalDate?,

    @field:NotNull(message = "Appointment time is required")
    val appointmentTime: LocalTime?
)

data class AppointmentResponse(
    val appointmentId: UUID,
    val patientId: UUID,
    val clinicId: UUID,
    val doctorId: UUID,
    val doctorName: String,
    val serviceId: UUID,
    val serviceName: String,
    val scheduleId: UUID,
    val appointmentAt: OffsetDateTime,
    val createdAt: OffsetDateTime
)
