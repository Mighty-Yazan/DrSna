package com.example.demo.controller

import com.example.demo.dto.ScheduleResponseDto
import com.example.demo.dto.WalletResponse
import com.example.demo.service.ScheduleAdminService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/doctor")
@PreAuthorize("hasRole('DOCTOR')")
class DoctorController(
    private val scheduleAdminService: ScheduleAdminService
) {

    // Schedule: doctor account type only — shows shifts assigned to them by their clinic(s)
    @GetMapping("/schedule")
    fun mySchedule(authentication: Authentication): ResponseEntity<List<ScheduleResponseDto>> =
        ResponseEntity.ok(scheduleAdminService.getMySchedule(authentication.name))

    // Wallet: static placeholder section
    @GetMapping("/wallet")
    fun wallet(): ResponseEntity<WalletResponse> =
        ResponseEntity.ok(WalletResponse())
}
