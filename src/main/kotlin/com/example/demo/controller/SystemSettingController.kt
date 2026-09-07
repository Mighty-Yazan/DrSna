package com.example.demo.controller

import com.example.demo.dto.SystemSettingDto
import com.example.demo.service.SystemSettingService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import org.springframework.security.access.prepost.PreAuthorize

@RestController
@RequestMapping("/api/v1/super-admin/settings")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class SystemSettingController(
    private val settingService: SystemSettingService
) {

    @GetMapping("/commission-rate")
    fun getDefaultCommissionRate(): ResponseEntity<SystemSettingDto> {
        val rate = settingService.getDefaultCommissionRate()
        return ResponseEntity.ok(SystemSettingDto(defaultCommissionRate = rate))
    }

    @PutMapping("/commission-rate")
    fun updateDefaultCommissionRate(
        @RequestParam rate: BigDecimal
    ): ResponseEntity<SystemSettingDto> {
        val updated = settingService.updateDefaultCommissionRate(rate)
        return ResponseEntity.ok(updated)
    }
}