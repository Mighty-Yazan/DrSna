package com.example.demo.controller

import com.example.demo.dto.ClinicManagementDto
import com.example.demo.service.SuperAdminClinicService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID
import java.math.BigDecimal
import org.springframework.security.access.prepost.PreAuthorize

@RestController
@RequestMapping("/api/v1/super-admin/clinics")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class SuperAdminClinicController(
    private val clinicService: SuperAdminClinicService
) {

    // جلب طلبات الانضمام المعلقة
    @GetMapping("/pending")
    fun getPendingClinics(): ResponseEntity<List<ClinicManagementDto>> {
        return ResponseEntity.ok(clinicService.getPendingClinics())
    }

    // قبول أو تغيير حالة التفعيل للعيادة
    @PutMapping("/{clinicUserId}/status")
    fun updateClinicStatus(
        @PathVariable clinicUserId: UUID,
        @RequestParam approve: Boolean
    ): ResponseEntity<ClinicManagementDto> {
        val updatedClinic = clinicService.updateClinicStatus(clinicUserId, approve)
        return ResponseEntity.ok(updatedClinic)
    }

    // جلب العيادات النشطة مع إمكانية الفلترة بالبحث
    @GetMapping("/active")
    fun getActiveClinics(
        @RequestParam(required = false) search: String?
    ): ResponseEntity<List<ClinicManagementDto>> {
        return ResponseEntity.ok(clinicService.getActiveClinics(search))
    }

    // تعديل نسبة العمولة لعيادة معينة (مثال الممرر: 0.15 لنسبة 15% أو 0.00 للعيادة المجانية)
    @PutMapping("/{clinicUserId}/commission-rate")
    fun updateCommissionRate(
        @PathVariable clinicUserId: UUID,
        @RequestParam rate: BigDecimal
    ): ResponseEntity<ClinicManagementDto> {
        val updatedClinic = clinicService.updateClinicCommissionRate(clinicUserId, rate)
        return ResponseEntity.ok(updatedClinic)
    }
}