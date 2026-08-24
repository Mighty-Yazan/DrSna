package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.model.City
import com.example.demo.service.PatientService
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
@RequestMapping("/api/patient")
@PreAuthorize("hasRole('PATIENT')")
class PatientController(private val patientService: PatientService) {

    @GetMapping("/clinics")
    fun searchClinics(
        authentication: Authentication,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) service: String?,
        @RequestParam(required = false) city: City?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(defaultValue = "false") availableOnly: Boolean
    ): ResponseEntity<List<ClinicSummaryResponse>> = ResponseEntity.ok(
        patientService.searchClinics(authentication.name, name, service, city, date, availableOnly)
    )

    @GetMapping("/clinics/{clinicId}")
    fun clinicDetails(@PathVariable clinicId: UUID): ResponseEntity<ClinicDetailsResponse> =
        ResponseEntity.ok(patientService.getClinicDetails(clinicId))

    @GetMapping("/clinics/{clinicId}/availability")
    fun availability(
        @PathVariable clinicId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestParam(required = false) doctorId: UUID?
    ): ResponseEntity<List<AvailabilitySlotResponse>> =
        ResponseEntity.ok(patientService.getAvailability(clinicId, date, doctorId))

    @PostMapping("/appointments")
    fun createAppointment(
        authentication: Authentication,
        @Valid @RequestBody request: CreateAppointmentRequest
    ): ResponseEntity<AppointmentResponse> =
        ResponseEntity.status(HttpStatus.CREATED)
            .body(patientService.createAppointment(authentication.name, request))
}
