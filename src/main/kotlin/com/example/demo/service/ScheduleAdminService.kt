package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.exception.AppException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.*
import com.example.demo.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Service
class ScheduleAdminService(
    private val scheduleRepository: ScheduleRepository,
    private val clinicRepository: ClinicRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository,
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository
) {
    private val zoneId = ZoneId.of("Asia/Amman")

    @Transactional
    fun save(clinicEmail: String, request: SaveScheduleRequest): ScheduleResponseDto {
        return when (request.type) {
            ScheduleType.CLINIC_HOURS -> {
                val day = request.dayOfWeek ?: throw AppException("dayOfWeek is required")
                val start = request.startTime ?: throw AppException("startTime is required")
                val end = request.endTime ?: throw AppException("endTime is required")
                saveClinicHours(clinicEmail, SaveClinicHoursRequest(day, start, end))
            }
            ScheduleType.DOCTOR_SHIFT -> {
                val doctorId = request.doctorId ?: throw AppException("doctorId is required")
                val date = request.specificDate ?: throw AppException("specificDate is required")
                val start = request.startTime ?: throw AppException("startTime is required")
                val end = request.endTime ?: throw AppException("endTime is required")
                saveDoctorSchedule(clinicEmail, SaveDoctorScheduleRequest(doctorId, date, start, end))
            }
            ScheduleType.HOLIDAY -> {
                val date = request.specificDate ?: throw AppException("specificDate is required")
                saveHoliday(clinicEmail, SaveHolidayRequest(date, request.reason, request.doctorId))
            }
            else -> throw AppException("Unsupported schedule type")
        }
    }

    @Transactional(readOnly = true)
    fun getClinicHours(clinicEmail: String): List<ScheduleResponseDto> {
        val clinic = getClinic(clinicEmail)
        return scheduleRepository.findByClinicId(clinic.id!!)
            .filter { it.type == ScheduleType.CLINIC_HOURS }
            .map { it.toResponseDto() }
    }

    @Transactional
    fun saveClinicHours(clinicEmail: String, request: SaveClinicHoursRequest): ScheduleResponseDto {
        validateTimes(request.startTime, request.endTime)
        val clinic = getClinic(clinicEmail)
        val schedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(clinic.id!!, ScheduleType.CLINIC_HOURS, request.dayOfWeek)
            ?: Schedule(type = ScheduleType.CLINIC_HOURS, clinic = clinic)
        schedule.dayOfWeek = request.dayOfWeek
        schedule.startTime = request.startTime
        schedule.endTime = request.endTime
        return scheduleRepository.save(schedule).toResponseDto()
    }

    @Transactional
    fun deleteClinicHoursDay(clinicEmail: String, dayOfWeek: java.time.DayOfWeek) {
        val clinic = getClinic(clinicEmail)
        scheduleRepository.deleteByClinicIdAndTypeAndDayOfWeek(
            clinicId = clinic.id!!,
            type = ScheduleType.CLINIC_HOURS,
            dayOfWeek = dayOfWeek
        )
    }

    @Transactional
    fun saveDoctorSchedule(clinicEmail: String, request: SaveDoctorScheduleRequest): ScheduleResponseDto {
        validateTimes(request.startTime, request.endTime)
        val clinic = getClinic(clinicEmail)
        val clinicUserId = clinic.user!!.id!!
        val doctorId = UUID.fromString(request.doctorId)
        val relation = clinicDoctorRepository.findAllByClinic_Id(clinicUserId).firstOrNull { it.doctor?.id == doctorId }
            ?: throw ResourceNotFoundException("Doctor is not associated with your clinic")
        if (relation.doctor?.isActive != true) throw AppException("Cannot create a schedule for an inactive doctor")

        val schedule = scheduleRepository.findByClinicId(clinic.id!!)
            .firstOrNull { it.doctor?.id == doctorId && it.type == ScheduleType.DOCTOR_SHIFT && it.specificDate == request.specificDate }
            ?: Schedule(type = ScheduleType.DOCTOR_SHIFT, clinic = clinic, doctor = relation.doctor)
        schedule.dayOfWeek = null
        schedule.specificDate = request.specificDate
        schedule.startTime = request.startTime
        schedule.endTime = request.endTime
        return scheduleRepository.save(schedule).toResponseDto()
    }

    @Transactional
    fun deleteDoctorScheduleDate(clinicEmail: String, doctorIdStr: String, specificDate: LocalDate) {
        val clinic = getClinic(clinicEmail)
        val doctorId = UUID.fromString(doctorIdStr)
        scheduleRepository.deleteByClinicIdAndDoctor_IdAndTypeAndSpecificDate(
            clinicId = clinic.id!!,
            doctorId = doctorId,
            type = ScheduleType.DOCTOR_SHIFT,
            specificDate = specificDate
        )
    }

    @Transactional
    fun saveHoliday(clinicEmail: String, request: SaveHolidayRequest): ScheduleResponseDto {
        val clinic = getClinic(clinicEmail)
        val doctor = request.doctorId?.let {
            val doctorId = UUID.fromString(it)
            val relation = clinicDoctorRepository.findAllByClinic_Id(clinic.user!!.id!!).firstOrNull { r -> r.doctor?.id == doctorId }
                ?: throw ResourceNotFoundException("Doctor is not associated with your clinic")
            relation.doctor
        }
        val schedule = Schedule(type = ScheduleType.HOLIDAY, specificDate = request.specificDate, reason = request.reason, clinic = clinic, doctor = doctor)
        return scheduleRepository.save(schedule).toResponseDto()
    }

    @Transactional(readOnly = true)
    fun getAvailableSlots(clinicEmail: String, doctorId: UUID, date: LocalDate): List<LocalTime> {
        val clinic = getClinic(clinicEmail)
        val relation = clinicDoctorRepository.findAllByClinic_Id(clinic.user!!.id!!).firstOrNull { it.doctor?.id == doctorId }
            ?: throw ResourceNotFoundException("Doctor is not associated with your clinic")
        if (relation.doctor?.isActive != true) return emptyList()

        val clinicHoliday = scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinic.id!!, date, ScheduleType.HOLIDAY)
        val allSchedules = scheduleRepository.findByClinicId(clinic.id!!)
        val doctorHoliday = allSchedules.any { it.doctor?.id == doctorId && it.type == ScheduleType.HOLIDAY && it.specificDate == date }
        if (clinicHoliday || doctorHoliday) return emptyList()

        val clinicSchedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(clinic.id!!, ScheduleType.CLINIC_HOURS, date.dayOfWeek) ?: return emptyList()
        val doctorSchedule = allSchedules.firstOrNull { it.doctor?.id == doctorId && it.type == ScheduleType.DOCTOR_SHIFT && it.dayOfWeek == date.dayOfWeek } ?: return emptyList()
        val start = maxOf(clinicSchedule.startTime!!, doctorSchedule.startTime!!)
        val end = minOf(clinicSchedule.endTime!!, doctorSchedule.endTime!!)
        if (!start.isBefore(end)) return emptyList()

        val from = date.atStartOfDay(zoneId).toInstant()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val booked = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctorId, from, to)
            .filter { it.status != AppointmentStatus.CANCELLED }
            .mapNotNull { it.appointmentDate }.toSet()

        return generateSequence(start) { current ->
            val next = current.plusMinutes(60)
            if (!next.isAfter(end)) next else null
        }.takeWhile { it.plusMinutes(60).let { endTime -> !endTime.isAfter(end) } }
            .filter { date.atTime(it).atZone(zoneId).toInstant() !in booked }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getDoctorSchedules(clinicEmail: String, doctorId: UUID): List<ScheduleResponseDto> {
        val clinic = getClinic(clinicEmail)
        return scheduleRepository.findByClinicId(clinic.id!!)
            .filter { it.doctor?.id == doctorId && it.type == ScheduleType.DOCTOR_SHIFT }
            .map { it.toResponseDto() }
    }

    private fun getClinic(email: String): Clinic = clinicRepository.findByUserEmail(email)
        .orElseThrow { ResourceNotFoundException("Clinic not found for authenticated user") }

    private fun validateTimes(start: LocalTime, end: LocalTime) {
        if (!start.isBefore(end)) throw AppException("Start time must be before end time")
    }

    private fun Schedule.toResponseDto() = ScheduleResponseDto(id, type, dayOfWeek, startTime, endTime, specificDate, reason, doctor?.id?.toString())
}