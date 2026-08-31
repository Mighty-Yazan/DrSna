package com.example.demo.controller

import com.example.demo.dto.AppointmentListResponse
import com.example.demo.dto.AppointmentSummaryResponse
import com.example.demo.dto.BookAppointmentRequest
import com.example.demo.dto.BookAppointmentResponse
import com.example.demo.dto.MessageResponse
import com.example.demo.dto.RescheduleAppointmentRequest
import com.example.demo.dto.UpdateAppointmentStatusRequest
import com.example.demo.model.AppointmentStatus
import com.example.demo.service.AppointmentService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
class AppointmentController(
    private val appointmentService: AppointmentService
) {

    // ============================================================
    // PATIENT APPOINTMENTS
    // ============================================================

    @GetMapping("/api/patient/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    fun myAppointments(
        authentication: Authentication,
        @RequestParam(
            required = false,
            defaultValue = "upcoming"
        )
        scope: String
    ): ResponseEntity<AppointmentListResponse> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.getMyAppointments(
                username,
                scope
            )
        )
    }

    @GetMapping("/api/patient/appointments/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    fun appointmentDetails(
        authentication: Authentication,
        @PathVariable appointmentId: UUID
    ): ResponseEntity<AppointmentSummaryResponse> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.getAppointmentForPatient(
                username,
                appointmentId
            )
        )
    }

    @DeleteMapping("/api/patient/appointments/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    fun cancelAppointment(
        authentication: Authentication,
        @PathVariable appointmentId: UUID
    ): ResponseEntity<MessageResponse> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.cancelAppointment(
                username,
                appointmentId
            )
        )
    }

    // ============================================================
    // DOCTOR APPOINTMENTS
    // ============================================================

    @GetMapping("/api/doctor/appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    fun doctorAppointments(
        authentication: Authentication,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<List<AppointmentSummaryResponse>> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.getDoctorAppointments(
                username,
                date
            )
        )
    }

    // ============================================================
    // CLINIC APPOINTMENTS
    // ============================================================

    @PostMapping("/api/clinic/appointments")
    @PreAuthorize("hasRole('CLINIC')")
    fun createWalkInAppointment(
        authentication: Authentication,
        @Valid
        @RequestBody
        request: com.example.demo.dto.CreateWalkInAppointmentRequest
    ): ResponseEntity<AppointmentSummaryResponse> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(
            appointmentService.createWalkInAppointment(
                username,
                request
            )
        )
    }

    @DeleteMapping("/api/clinic/appointments/{appointmentId}")
    @PreAuthorize("hasRole('CLINIC')")
    fun deleteWalkInAppointment(
        authentication: Authentication,
        @PathVariable appointmentId: UUID
    ): ResponseEntity<MessageResponse> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.deleteWalkInAppointment(
                username,
                appointmentId
            )
        )
    }

    @GetMapping("/api/clinic/appointments")
    @PreAuthorize("hasRole('CLINIC')")
    fun clinicAppointments(
        authentication: Authentication,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate?,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?,

        @RequestParam(required = false)
        doctorId: UUID?,

        @RequestParam(required = false)
        status: AppointmentStatus?
    ): ResponseEntity<List<AppointmentSummaryResponse>> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.getClinicAppointments(
                username,
                startDate,
                endDate,
                doctorId,
                status
            )
        )
    }

    // ============================================================
    // GENERIC APPOINTMENT LIST
    //
    // Test:
    // GET /api/appointments?status=PENDING&doctorId=...
    // ============================================================

    @GetMapping("/api/appointments")
    @PreAuthorize("hasAnyRole('CLINIC','DOCTOR','PATIENT')")
    fun appointments(
        authentication: Authentication,

        @RequestParam(required = false)
        status: AppointmentStatus?,

        @RequestParam(required = false)
        doctorId: UUID?,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<List<AppointmentSummaryResponse>> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        val role = authentication.authorities
            .firstOrNull {
                it.authority?.startsWith("ROLE_") == true
            }
            ?.authority
            ?.removePrefix("ROLE_")
            ?: throw IllegalStateException(
                "Authenticated role was not found"
            )

        return ResponseEntity.ok(
            appointmentService.getAppointments(
                username,
                role,
                status,
                doctorId,
                date
            )
        )
    }

    // ============================================================
    // UPDATE APPOINTMENT STATUS
    //
    // PATCH /api/appointments/{appointmentId}/status
    // ============================================================

    @PatchMapping("/api/appointments/{appointmentId}/status")
    @PreAuthorize("hasRole('CLINIC')")
    fun updateStatus(
        authentication: Authentication,
        @PathVariable appointmentId: UUID,
        @Valid
        @RequestBody
        request: UpdateAppointmentStatusRequest
    ): ResponseEntity<AppointmentSummaryResponse> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.updateStatus(
                username,
                appointmentId,
                request
            )
        )
    }

    // ============================================================
    // RESCHEDULE
    //
    // PATCH /api/appointments/{appointmentId}/reschedule
    // ============================================================

    @PatchMapping("/api/appointments/{appointmentId}/reschedule")
    @PreAuthorize("hasRole('CLINIC')")
    fun reschedule(
        authentication: Authentication,
        @PathVariable appointmentId: UUID,
        @Valid
        @RequestBody
        request: RescheduleAppointmentRequest
    ): ResponseEntity<AppointmentSummaryResponse> {

        val username = authentication.name
            ?: throw IllegalStateException(
                "Authenticated username was not found"
            )

        return ResponseEntity.ok(
            appointmentService.reschedule(
                username,
                appointmentId,
                request
            )
        )
    }

    // ============================================================
    // DOHA — PUBLIC APPOINTMENT FORM BOOKING
    // POST /api/appointments/book
    // No authentication required — open to all users from the frontend form
    // ============================================================

    @PostMapping("/api/appointments/book")
    fun bookAppointment(
        @Valid
        @RequestBody
        request: BookAppointmentRequest
    ): ResponseEntity<BookAppointmentResponse> {

        val response = appointmentService.bookAppointment(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
}