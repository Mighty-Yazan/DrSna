package com.example.demo.controller

import com.example.demo.dto.ScheduleRequest
import com.example.demo.dto.ScheduleResponse
import com.example.demo.dto.MessageResponse
import com.example.demo.service.ScheduleAdminService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID


@RestController
@RequestMapping("/api/clinic/schedules")
@PreAuthorize("hasRole('CLINIC')")
class ScheduleAdminController(private val service: ScheduleAdminService) {
    @PostMapping
    fun create(
        authentication: Authentication,
        @Valid @RequestBody request: ScheduleRequest
    ): ResponseEntity<ScheduleResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication.name, request))

    @DeleteMapping("/{scheduleId}")
    fun delete(
        authentication: Authentication,
        @PathVariable scheduleId: UUID
    ): ResponseEntity<MessageResponse> {
        service.delete(authentication.name, scheduleId)
        return ResponseEntity.ok(MessageResponse("Schedule deleted successfully"))
    }
}
