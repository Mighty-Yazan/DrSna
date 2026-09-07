package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.model.City
import com.example.demo.model.ClinicApplicationStatus
import com.example.demo.service.SuperAdminService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/super-admin")
@PreAuthorize("hasRole('ADMIN')")
class SuperAdminController(
    private val superAdminService: SuperAdminService
) {
    @GetMapping("/dashboard")
    fun dashboard(): ResponseEntity<DashboardSummaryResponse> =
        ResponseEntity.ok(superAdminService.getDashboard())

    @GetMapping("/clinics/count")
    fun clinicCount(): ResponseEntity<Map<String, Long>> =
        ResponseEntity.ok(mapOf("count" to superAdminService.getClinicCount()))

    @GetMapping("/clinics")
    fun clinics(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) city: City?,
        @RequestParam(required = false) status: ClinicApplicationStatus?,
        @RequestParam(required = false) active: Boolean?
    ): ResponseEntity<List<ClinicAdminListItem>> =
        ResponseEntity.ok(superAdminService.getClinics(search, city, status, active))

    @GetMapping("/clinics/pending")
    fun pendingClinics(): ResponseEntity<List<ClinicAdminListItem>> =
        ResponseEntity.ok(superAdminService.getPendingClinics())

    @GetMapping("/clinics/{clinicId}/review")
    fun reviewClinic(@PathVariable clinicId: UUID): ResponseEntity<ClinicApplicationReviewResponse> =
        ResponseEntity.ok(superAdminService.getClinicReview(clinicId))

    @PostMapping("/clinics/{clinicId}/approve")
    fun approveClinic(
        @PathVariable clinicId: UUID,
        authentication: org.springframework.security.core.Authentication
    ): ResponseEntity<ClinicApplicationReviewResponse> =
        ResponseEntity.ok(superAdminService.approveClinic(authentication.name, clinicId))

    @PostMapping("/clinics/{clinicId}/reject")
    fun rejectClinic(
        @PathVariable clinicId: UUID,
        @Valid @RequestBody request: RejectClinicRequest,
        authentication: org.springframework.security.core.Authentication
    ): ResponseEntity<ClinicApplicationReviewResponse> =
        ResponseEntity.ok(superAdminService.rejectClinic(authentication.name, clinicId, request))

    @PutMapping("/clinics/{clinicId}/commission")
    fun overrideCommission(
        @PathVariable clinicId: UUID,
        @Valid @RequestBody request: CommissionOverrideRequest,
        authentication: org.springframework.security.core.Authentication
    ): ResponseEntity<ClinicApplicationReviewResponse> =
        ResponseEntity.ok(superAdminService.overrideCommission(authentication.name, clinicId, request))

    @DeleteMapping("/clinics/{clinicId}")
    fun removeClinic(
        @PathVariable clinicId: UUID,
        authentication: org.springframework.security.core.Authentication
    ): ResponseEntity<Void> {
        superAdminService.removeClinic(authentication.name, clinicId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/fx-rates")
    fun fxRates(): ResponseEntity<List<FxRateResponse>> =
        ResponseEntity.ok(superAdminService.getFxRates())

    @GetMapping("/activities")
    fun activities(): ResponseEntity<List<AdminActivityResponse>> =
        ResponseEntity.ok(superAdminService.getRecentActivities())

    @GetMapping("/top-clinics")
    fun topClinics(): ResponseEntity<List<TopClinicCommissionResponse>> =
        ResponseEntity.ok(superAdminService.getTopClinics())

    @GetMapping("/commission-revenue/last-6-months")
    fun last6MonthsRevenue(): ResponseEntity<List<MonthlyCommissionRevenueResponse>> =
        ResponseEntity.ok(superAdminService.getLast6MonthsRevenue())

    @PutMapping("/fx-rates")
    fun upsertFxRate(
        @Valid @RequestBody request: UpsertFxRateRequest,
        authentication: org.springframework.security.core.Authentication
    ): ResponseEntity<FxRateResponse> =
        ResponseEntity.status(HttpStatus.OK).body(superAdminService.upsertFxRate(authentication.name, request))
}