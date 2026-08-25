package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.service.ScheduleAdminService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RestController
@RequestMapping("/api/clinic/schedules")
@PreAuthorize("hasRole('CLINIC')")
class ScheduleAdminController(
    private val scheduleAdminService: ScheduleAdminService
) {
    @PostMapping
    fun save(authentication: Authentication, @RequestBody request: SaveScheduleRequest): ResponseEntity<ScheduleResponseDto> =
        ResponseEntity.ok(scheduleAdminService.save(authentication.name, request))

    @PostMapping("/clinic-hours")
    fun saveClinicHours(authentication: Authentication, @RequestBody request: SaveClinicHoursRequest): ResponseEntity<ScheduleResponseDto> =
        ResponseEntity.ok(scheduleAdminService.saveClinicHours(authentication.name, request))

    @PostMapping("/doctor-schedule")
    fun saveDoctorSchedule(authentication: Authentication, @RequestBody request: SaveDoctorScheduleRequest): ResponseEntity<ScheduleResponseDto> =
        ResponseEntity.ok(scheduleAdminService.saveDoctorSchedule(authentication.name, request))

    @PostMapping("/holidays")
    fun saveHoliday(authentication: Authentication, @RequestBody request: SaveHolidayRequest): ResponseEntity<ScheduleResponseDto> =
        ResponseEntity.ok(scheduleAdminService.saveHoliday(authentication.name, request))

    @GetMapping("/available-slots")
    fun getAvailableSlots(
        authentication: Authentication,
        @RequestParam doctorId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<List<LocalTime>> =
        ResponseEntity.ok(scheduleAdminService.getAvailableSlots(authentication.name, doctorId, date))
}