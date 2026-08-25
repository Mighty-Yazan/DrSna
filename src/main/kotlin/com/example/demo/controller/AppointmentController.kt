package com.example.demo.controller

import com.example.demo.dto.AppointmentListResponse
import com.example.demo.dto.AppointmentSummaryResponse
import com.example.demo.dto.MessageResponse
import com.example.demo.service.AppointmentService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
class AppointmentController(private val appointmentService: AppointmentService) {

    @GetMapping("/api/patient/appointments")
    @PreAuthorize("hasRole('PATIENT')")
    fun myAppointments(
        authentication: Authentication,
        @RequestParam(required = false, defaultValue = "upcoming") scope: String
    ): ResponseEntity<AppointmentListResponse> =
        ResponseEntity.ok(appointmentService.getMyAppointments(authentication.name, scope))

    @GetMapping("/api/patient/appointments/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    fun appointmentDetails(
        authentication: Authentication,
        @PathVariable appointmentId: UUID
    ): ResponseEntity<AppointmentSummaryResponse> =
        ResponseEntity.ok(appointmentService.getAppointmentForPatient(authentication.name, appointmentId))

    @DeleteMapping("/api/patient/appointments/{appointmentId}")
    @PreAuthorize("hasRole('PATIENT')")
    fun cancelAppointment(
        authentication: Authentication,
        @PathVariable appointmentId: UUID
    ): ResponseEntity<MessageResponse> =
        ResponseEntity.ok(appointmentService.cancelAppointment(authentication.name, appointmentId))

    @GetMapping("/api/doctor/appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    fun doctorAppointments(
        authentication: Authentication,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?
    ): ResponseEntity<List<AppointmentSummaryResponse>> =
        ResponseEntity.ok(appointmentService.getDoctorAppointments(authentication.name, date))

    @GetMapping("/api/clinic/appointments")
    @PreAuthorize("hasRole('CLINIC')")
    fun clinicAppointments(
        authentication: Authentication,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false) doctorId: UUID?
    ): ResponseEntity<List<AppointmentSummaryResponse>> =
        ResponseEntity.ok(appointmentService.getClinicAppointments(authentication.name, date, doctorId))
}
