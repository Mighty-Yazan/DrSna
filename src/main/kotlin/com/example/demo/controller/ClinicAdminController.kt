package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.service.ClinicAdminService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/clinic")
@PreAuthorize("hasRole('CLINIC')")
class ClinicAdminController(
    private val clinicAdminService: ClinicAdminService
) {
    // ── CLINIC PROFILE ENDPOINTS ────────────────────────────────────────

    @GetMapping("/profile")
    fun getProfile(authentication: Authentication): ResponseEntity<ClinicProfileResponse> {
        val profile = clinicAdminService.getProfile(authentication.name)
        return ResponseEntity.ok(profile)
    }

    @PutMapping("/profile")
    fun updateProfile(
        authentication: Authentication,
        @Valid @RequestBody request: ClinicProfileRequest
    ): ResponseEntity<ClinicProfileResponse> {
        val updatedProfile = clinicAdminService.updateProfile(authentication.name, request)
        return ResponseEntity.ok(updatedProfile)
    }

    // ── SERVICES ENDPOINTS ──────────────────────────────────────────────

    @GetMapping("/services")
    fun getServices(authentication: Authentication): ResponseEntity<List<ServicesResponse>> {
        val services = clinicAdminService.getServices(authentication.name)
        return ResponseEntity.ok(services)
    }

    @PostMapping("/services")
    fun addService(
        authentication: Authentication,
        @Valid @RequestBody request: ServicesRequest
    ): ResponseEntity<ServicesResponse> {
        val createdService = clinicAdminService.addService(authentication.name, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdService)
    }

    @PutMapping("/services/{id}")
    fun updateService(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ServicesRequest
    ): ResponseEntity<ServicesResponse> {
        val updatedService = clinicAdminService.updateService(authentication.name, id, request)
        return ResponseEntity.ok(updatedService)
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteService(
        authentication: Authentication,
        @PathVariable id: UUID
    ): ResponseEntity<MessageResponse> {
        clinicAdminService.deleteService(authentication.name, id)
        return ResponseEntity.ok(MessageResponse("Service deleted successfully"))
    }

    // ── DOCTORS ENDPOINTS ───────────────────────────────────────────────

    @GetMapping("/doctors")
    fun getDoctors(authentication: Authentication): ResponseEntity<List<DoctorResponse>> {
        val doctors = clinicAdminService.getDoctors(authentication.name)
        return ResponseEntity.ok(doctors)
    }

    @PostMapping("/doctors")
    fun addDoctor(
        authentication: Authentication,
        @Valid @RequestBody request: AddDoctorRequest
    ): ResponseEntity<DoctorResponse> {
        val createdDoctor = clinicAdminService.addDoctor(authentication.name, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdDoctor)
    }

    @PutMapping("/doctors/{doctorId}")
    fun updateDoctor(
        authentication: Authentication,
        @PathVariable doctorId: UUID,
        @Valid @RequestBody request: UpdateDoctorRequest
    ): ResponseEntity<DoctorResponse> {
        val updatedDoctor = clinicAdminService.updateDoctor(authentication.name, doctorId, request)
        return ResponseEntity.ok(updatedDoctor)
    }

    @PatchMapping("/doctors/{doctorId}/toggle-status")
    fun toggleDoctorStatus(
        authentication: Authentication,
        @PathVariable doctorId: UUID
    ): ResponseEntity<MessageResponse> {
        val response = clinicAdminService.toggleDoctorStatus(authentication.name, doctorId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/doctors/{doctorId}")
    fun deleteDoctor(
        authentication: Authentication,
        @PathVariable doctorId: UUID
    ): ResponseEntity<MessageResponse> {
        clinicAdminService.deleteDoctor(authentication.name, doctorId)
        return ResponseEntity.ok(MessageResponse("Doctor deleted successfully from your clinic"))
    }
}