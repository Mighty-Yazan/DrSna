package com.example.demo.dto

import com.example.demo.model.AppointmentStatus
import com.example.demo.model.City
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
    val description: String?,
    val workingHours: String?,
    val services: List<String>,
    val specialties: List<String> = emptyList(),
    val doctors: List<String> = emptyList(),
    val available: Boolean? = null,
    val nextAvailableSlot: String? = null
)

data class ClinicDetailsResponse(
    val clinicId: UUID,
    val clinicUserId: UUID,
    val clinicName: String,
    val city: City,
    val rating: BigDecimal,
    val detailedAddress: String?,
    val phoneNumber: String?,
    val email: String?,
    val socialLinks: String?,
    val checkingFee: BigDecimal?,
    val description: String?,
    val workingHours: String?,
    val clinicHours: List<ScheduleResponse> = emptyList(),
    val services: List<ServiceDetailsResponse>,
    val specialties: List<String> = emptyList(),
    val doctors: List<DoctorDetailsResponse>,
    val reviews: List<ReviewResponse> = emptyList()
)

data class ServiceDetailsResponse(val serviceId: Any, val serviceName: String, val description: String?)

data class DoctorDetailsResponse(
    val doctorId: UUID,
    val fullName: String,
    val email: String,
    val specialty: String?,
    val bio: String?,
    val isActive: Boolean,
    val schedules: List<ScheduleResponse>
)

data class ScheduleResponse(
    val scheduleId: Any?,
    val dayOfWeek: DayOfWeek?,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val workingHoursDoctor: String?
)

data class AvailabilitySlotResponse(
    val scheduleId: Any?,
    val doctorId: UUID,
    val doctorName: String,
    val date: LocalDate,
    val time: LocalTime,
    val appointmentAt: OffsetDateTime,
    val available: Boolean
)

// -------------------- Appointment booking --------------------
data class CreateAppointmentRequest(
    @field:NotNull(message = "Clinic ID is required") val clinicId: UUID?,
    @field:NotNull(message = "Doctor ID is required") val doctorId: UUID?,
    @field:NotNull(message = "Service IDs are required") val serviceIds: List<UUID>,
    @field:NotNull(message = "Schedule ID is required") val scheduleId: Any?,
    @field:NotNull(message = "Appointment date is required") val appointmentDate: LocalDate?,
    @field:NotNull(message = "Appointment time is required") val appointmentTime: LocalTime?
)

data class AppointmentResponse(
    val appointmentId: Any,
    val patientId: UUID,
    val clinicId: UUID,
    val doctorId: UUID,
    val doctorName: String,
    val serviceIds: List<Any>,
    val serviceNames: List<String>,
    val scheduleId: Any?,
    val appointmentAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
    val status: AppointmentStatus
)

data class AppointmentFilterResponse(
    val appointmentId: UUID,
    val patientId: UUID,
    val patientName: String,
    val clinicId: UUID,
    val clinicName: String,
    val doctorId: UUID,
    val doctorName: String,
    val serviceIds: List<UUID>,
    val serviceNames: List<String>,
    val appointmentAt: OffsetDateTime,
    val status: AppointmentStatus,
    val createdAt: OffsetDateTime
)

data class UpdateAppointmentStatusRequest(@field:NotNull val status: AppointmentStatus)
data class RescheduleAppointmentRequest(
    @field:NotNull val appointmentDate: LocalDate?,
    @field:NotNull val appointmentTime: LocalTime?
)

data class CreateReviewRequest(
    @field:NotNull val appointmentId: UUID?,
    @field:Min(1) @field:Max(5) val rating: Int,
    val comment: String? = null
)

data class ReviewResponse(
    val reviewId: UUID,
    val appointmentId: UUID,
    val patientId: UUID,
    val patientName: String,
    val doctorId: UUID,
    val doctorName: String,
    val rating: Int,
    val comment: String?,
    val reply: String?,
    val replyAt: OffsetDateTime?,
    val createdAt: OffsetDateTime
)