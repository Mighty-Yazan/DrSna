package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.model.AppointmentStatus
import com.example.demo.service.AppointmentService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/appointments")
class AppointmentController(private val appointmentService: AppointmentService) {
    @GetMapping
    fun list(
        authentication: Authentication,
        @RequestParam(required = false) status: AppointmentStatus?,
        @RequestParam(required = false) doctorId: UUID?
    ): ResponseEntity<List<AppointmentFilterResponse>> =
        ResponseEntity.ok(appointmentService.listAppointments(authentication.name, status, doctorId))

    @PatchMapping("/{appointmentId}/status")
    fun updateStatus(
        authentication: Authentication,
        @PathVariable appointmentId: UUID,
        @Valid @RequestBody request: UpdateAppointmentStatusRequest
    ): ResponseEntity<AppointmentFilterResponse> =
        ResponseEntity.ok(appointmentService.updateStatus(authentication.name, appointmentId, request))

    @PutMapping("/{appointmentId}/reschedule")
    fun reschedule(
        authentication: Authentication,
        @PathVariable appointmentId: UUID,
        @Valid @RequestBody request: RescheduleAppointmentRequest
    ): ResponseEntity<AppointmentFilterResponse> =
        ResponseEntity.ok(appointmentService.reschedule(authentication.name, appointmentId, request))
}