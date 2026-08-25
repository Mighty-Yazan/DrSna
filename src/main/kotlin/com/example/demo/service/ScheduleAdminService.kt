package com.example.demo.service

import com.example.demo.dto.ScheduleRequest
import com.example.demo.dto.ScheduleResponse
import com.example.demo.exception.AppException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.Role
import com.example.demo.model.Schedule
import com.example.demo.repository.ClinicDoctorRepository
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.ScheduleRepository
import com.example.demo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

@Service
class ScheduleAdminService(
    private val clinicRepository: ClinicRepository,
    private val userRepository: UserRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository,
    private val scheduleRepository: ScheduleRepository
) {
    @Transactional(readOnly = true)
    fun list(userEmail: String): List<ScheduleResponse> {
        val clinic = clinicRepository.findByUserEmail(userEmail)
            .orElseThrow { ResourceNotFoundException("Clinic profile not found") }

        return scheduleRepository.findAllByClinicIdOrderByDoctorUserIdAscDayOfWeekAscStartTimeAsc(clinic.id!!)
            .map { it.toResponse() }
    }

    @Transactional
    fun create(userEmail: String, request: ScheduleRequest): ScheduleResponse {
        val clinic = clinicRepository.findByUserEmail(userEmail)
            .orElseThrow { ResourceNotFoundException("Clinic profile not found") }

        val doctorId = request.doctorUserId
        val day = DayOfWeek.valueOf(request.dayOfWeek.uppercase())
        val start = LocalTime.parse(request.startTime)
        val end = LocalTime.parse(request.endTime)

        if (!start.isBefore(end)) {
            throw AppException("Start time must be before end time")
        }

        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinic.user!!.id!!, doctorId)) {
            throw AppException("Doctor is not associated with this clinic")
        }

        val doctor = userRepository.findById(doctorId)
            .orElseThrow { ResourceNotFoundException("Doctor not found") }

        if (doctor.role != Role.DOCTOR) {
            throw AppException("Selected user is not a doctor")
        }

        val savedSchedule = scheduleRepository.save(
            Schedule(
                clinicId = clinic.id!!,
                doctorUserId = doctorId,
                dayOfWeek = day,
                startTime = start,
                endTime = end,
                workingHoursDoctor = request.workingHoursDoctor?.trim()
            )
        )

        return savedSchedule.toResponse()
    }

    @Transactional
    fun delete(userEmail: String, scheduleId: UUID) {
        val clinic = clinicRepository.findByUserEmail(userEmail)
            .orElseThrow { ResourceNotFoundException("Clinic profile not found") }

        val schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow { ResourceNotFoundException("Schedule not found") }

        if (schedule.clinicId != clinic.id) {
            throw AppException("You do not have permission to delete this schedule")
        }

        scheduleRepository.delete(schedule)
    }

    private fun Schedule.toResponse() = ScheduleResponse(
        scheduleId = id!!,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        workingHoursDoctor = workingHoursDoctor
    )
}