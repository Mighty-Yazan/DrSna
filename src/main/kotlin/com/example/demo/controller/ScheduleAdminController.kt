package com.example.demo.controller

import com.example.demo.dto.*
import com.example.demo.service.ScheduleAdminService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@RestController
@RequestMapping("/api/admin/schedules")
class ScheduleAdminController(
    private val scheduleAdminService: ScheduleAdminService
) {

    @PostMapping("/clinic-hours")
    fun saveClinicHours(
        @RequestParam clinicId: UUID,
        @RequestBody request: SaveClinicHoursRequest
    ): ResponseEntity<ScheduleResponseDto> {
        return ResponseEntity.ok(scheduleAdminService.saveClinicHours(clinicId, request))
    }

    @PostMapping("/doctor-schedule")
    fun saveDoctorSchedule(
        @RequestParam clinicId: UUID,
        @RequestBody request: SaveDoctorScheduleRequest
    ): ResponseEntity<ScheduleResponseDto> {
        return ResponseEntity.ok(scheduleAdminService.saveDoctorSchedule(clinicId, request))
    }

    @PostMapping("/holidays")
    fun saveHoliday(
        @RequestParam clinicId: UUID,
        @RequestBody request: SaveHolidayRequest
    ): ResponseEntity<ScheduleResponseDto> {
        return ResponseEntity.ok(scheduleAdminService.saveHoliday(clinicId, request))
    }

    @GetMapping("/available-slots")
    fun getAvailableSlots(
        @RequestParam clinicId: UUID,
        @RequestParam doctorId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ResponseEntity<List<LocalTime>> {
        return ResponseEntity.ok(scheduleAdminService.getAvailableSlots(clinicId, doctorId, date))
    }
}