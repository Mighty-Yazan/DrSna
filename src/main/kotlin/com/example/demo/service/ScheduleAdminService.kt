package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.model.Schedule
import com.example.demo.model.ScheduleType
import com.example.demo.repository.AppointmentRepository
import com.example.demo.repository.ClinicDoctorRepository
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.ScheduleRepository
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
    private val appointmentRepository: AppointmentRepository
) {
    private val zoneId = ZoneId.of("Asia/Amman")

    @Transactional
    fun saveClinicHours(clinicId: UUID, request: SaveClinicHoursRequest): ScheduleResponseDto {
        val clinic = clinicRepository.findById(clinicId)
            .orElseThrow { RuntimeException("العيادة غير موجودة") }

        val schedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(
            clinicId, ScheduleType.CLINIC_HOURS, request.dayOfWeek
        ) ?: Schedule(type = ScheduleType.CLINIC_HOURS, clinic = clinic)

        schedule.dayOfWeek = request.dayOfWeek
        schedule.startTime = request.startTime
        schedule.endTime = request.endTime

        return scheduleRepository.save(schedule).toResponseDto()
    }

    @Transactional
    fun saveDoctorSchedule(clinicId: UUID, request: SaveDoctorScheduleRequest): ScheduleResponseDto {
        val clinic = clinicRepository.findById(clinicId)
            .orElseThrow { RuntimeException("العيادة غير موجودة") }

        val doctor = clinicDoctorRepository.findAll()
            .firstOrNull { it.doctor?.id.toString() == request.doctorId || it.id.toString() == request.doctorId }
            ?.doctor
            ?: throw RuntimeException("الطبيب غير موجود")

        val schedule = scheduleRepository.findByClinicId(clinicId)
            .firstOrNull { it.doctor?.id == doctor.id && it.type == ScheduleType.DOCTOR_SHIFT && it.dayOfWeek == request.dayOfWeek }
            ?: Schedule(type = ScheduleType.DOCTOR_SHIFT, clinic = clinic, doctor = doctor)

        schedule.dayOfWeek = request.dayOfWeek
        schedule.startTime = request.startTime
        schedule.endTime = request.endTime

        return scheduleRepository.save(schedule).toResponseDto()
    }

    @Transactional
    fun saveHoliday(clinicId: UUID, request: SaveHolidayRequest): ScheduleResponseDto {
        val clinic = clinicRepository.findById(clinicId)
            .orElseThrow { RuntimeException("العيادة غير موجودة") }

        val doctor = request.doctorId?.let { docId ->
            clinicDoctorRepository.findAll().firstOrNull { it.doctor?.id.toString() == docId }?.doctor
                ?: throw RuntimeException("الطبيب غير موجود")
        }

        val schedule = Schedule(
            type = ScheduleType.HOLIDAY,
            specificDate = request.specificDate,
            reason = request.reason,
            clinic = clinic,
            doctor = doctor
        )

        return scheduleRepository.save(schedule).toResponseDto()
    }

    @Transactional(readOnly = true)
    fun getAvailableSlots(
        clinicId: UUID,
        doctorId: String,
        date: LocalDate,
        slotDurationMinutes: Long = 60 // تم التعديل: القيمة الافتراضية أصبحت 60 دقيقة
    ): List<LocalTime> {
        val isClinicHoliday = scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinicId, date, ScheduleType.HOLIDAY)
        val doctorSchedules = scheduleRepository.findByClinicId(clinicId)

        val isDoctorHoliday = doctorSchedules.any {
            it.doctor?.id.toString() == doctorId && it.type == ScheduleType.HOLIDAY && it.specificDate == date
        }

        if (isClinicHoliday || isDoctorHoliday) {
            return emptyList()
        }

        val dayOfWeek = date.dayOfWeek

        val clinicSchedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(clinicId, ScheduleType.CLINIC_HOURS, dayOfWeek)
            ?: return emptyList()

        val doctorSchedule = doctorSchedules.firstOrNull {
            it.doctor?.id.toString() == doctorId && it.type == ScheduleType.DOCTOR_SHIFT && it.dayOfWeek == dayOfWeek
        } ?: return emptyList()

        val startShift = maxOf(clinicSchedule.startTime!!, doctorSchedule.startTime!!)
        val endShift = minOf(clinicSchedule.endTime!!, doctorSchedule.endTime!!)

        if (startShift >= endShift) return emptyList()

        val doctorUuid = try { UUID.fromString(doctorId) } catch (e: Exception) { return emptyList() }
        val from = date.atStartOfDay(zoneId).toInstant()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val bookedInstants = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctorUuid, from, to)
            .mapNotNull { it.appointmentDate }
            .toSet()

        val availableSlots = mutableListOf<LocalTime>()
        var currentSlot = startShift

        // تم التعديل: استخدام !isAfter لضمان دقة المقارنة مع كائنات LocalTime
        while (!currentSlot.plusMinutes(slotDurationMinutes).isAfter(endShift)) {
            val slotInstant = date.atTime(currentSlot).atZone(zoneId).toInstant()
            if (slotInstant !in bookedInstants) {
                availableSlots.add(currentSlot)
            }
            currentSlot = currentSlot.plusMinutes(slotDurationMinutes)
        }

        return availableSlots
    }

    private fun Schedule.toResponseDto() = ScheduleResponseDto(
        id = this.id,
        type = this.type,
        dayOfWeek = this.dayOfWeek,
        startTime = this.startTime,
        endTime = this.endTime,
        specificDate = this.specificDate,
        reason = this.reason,
        doctorId = this.doctor?.id?.toString()
    )
}