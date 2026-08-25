package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.exception.AppException
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.*
import com.example.demo.repository.*
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.*
import java.util.UUID

@Service
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository,
    private val scheduleRepository: ScheduleRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository
) {
    private val zoneId = ZoneId.of("Asia/Amman")

    @Transactional(readOnly = true)
    fun listAppointments(email: String, status: AppointmentStatus?, doctorId: UUID?): List<AppointmentFilterResponse> {
        val user = userRepository.findByEmail(email).orElseThrow { ResourceNotFoundException("User not found") }
        return appointmentRepository.findAll()
            .asSequence()
            .filter { appointment ->
                when (user.role) {
                    Role.CLINIC -> appointment.clinic?.id == user.id
                    Role.PATIENT -> appointment.patient?.id == user.id
                    Role.DOCTOR -> appointment.doctor?.id == user.id
                    else -> true
                }
            }
            .filter { status == null || it.status == status }
            .filter { doctorId == null || it.doctor?.id == doctorId }
            .sortedBy { it.appointmentDate }
            .map { it.toFilterResponse() }
            .toList()
    }

    @Transactional
    fun updateStatus(email: String, appointmentId: UUID, request: UpdateAppointmentStatusRequest): AppointmentFilterResponse {
        val clinicUser = userRepository.findByEmail(email).orElseThrow { ResourceNotFoundException("User not found") }
        if (clinicUser.role != Role.CLINIC) throw AppException("Only a clinic can update appointment status")
        val appointment = getAppointment(appointmentId)
        if (appointment.clinic?.id != clinicUser.id) throw AppException("You cannot modify this appointment")

        val newStatus = request.status
        if (appointment.status == AppointmentStatus.CANCELLED && newStatus != AppointmentStatus.CANCELLED) {
            throw AppException("A cancelled appointment cannot be reactivated")
        }
        appointment.status = newStatus
        return appointmentRepository.save(appointment).toFilterResponse()
    }

    @Transactional
    fun reschedule(email: String, appointmentId: UUID, request: RescheduleAppointmentRequest): AppointmentFilterResponse {
        val user = userRepository.findByEmail(email).orElseThrow { ResourceNotFoundException("User not found") }
        val appointment = getAppointment(appointmentId)
        val allowed = when (user.role) {
            Role.CLINIC -> appointment.clinic?.id == user.id
            Role.PATIENT -> appointment.patient?.id == user.id
            else -> false
        }
        if (!allowed) throw AppException("You cannot reschedule this appointment")
        if (appointment.status == AppointmentStatus.CANCELLED || appointment.status == AppointmentStatus.COMPLETED) {
            throw AppException("This appointment cannot be rescheduled")
        }

        val date = request.appointmentDate ?: throw AppException("Appointment date is required")
        val time = request.appointmentTime ?: throw AppException("Appointment time is required")
        if (date.isBefore(LocalDate.now(zoneId))) throw AppException("Appointment date cannot be in the past")
        if (time.minute != 0 || time.second != 0 || time.nano != 0) throw AppException("Appointments must start on a 60-minute slot")

        val clinicUserId = appointment.clinic!!.id!!
        val clinic = clinicRepository.findByUserId(clinicUserId).orElseThrow { ResourceNotFoundException("Clinic not found") }
        val doctor = appointment.doctor!!
        if (!doctor.isActive) throw AppException("Doctor account is inactive")
        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinicUserId, doctor.id!!)) throw AppException("Doctor is not associated with the clinic")

        val schedules = scheduleRepository.findByClinicId(clinic.id!!)
        if (scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinic.id!!, date, ScheduleType.HOLIDAY)) throw AppException("Clinic is closed on the selected date")
        if (schedules.any { it.doctor?.id == doctor.id && it.type == ScheduleType.HOLIDAY && it.specificDate == date }) throw AppException("Doctor is unavailable on the selected date")

        val clinicSchedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(clinic.id!!, ScheduleType.CLINIC_HOURS, date.dayOfWeek) ?: throw AppException("Clinic is closed on this day")
        val doctorSchedule = schedules.firstOrNull { it.doctor?.id == doctor.id && it.type == ScheduleType.DOCTOR_SHIFT && it.dayOfWeek == date.dayOfWeek } ?: throw AppException("Doctor is not scheduled on this day")
        val start = maxOf(clinicSchedule.startTime!!, doctorSchedule.startTime!!)
        val end = minOf(clinicSchedule.endTime!!, doctorSchedule.endTime!!)
        if (time.isBefore(start) || time.plusMinutes(60).isAfter(end)) throw AppException("Selected time is outside available working hours")

        val newInstant = date.atTime(time).atZone(zoneId).toInstant()
        if (newInstant.isBefore(Instant.now())) throw AppException("Appointment time must be in the future")
        val conflict = appointmentRepository.existsByDoctorIdAndAppointmentDate(doctor.id!!, newInstant)
        if (conflict && appointment.appointmentDate != newInstant) throw DuplicateResourceException("The selected appointment slot is already booked")

        appointment.appointmentDate = newInstant
        appointment.status = AppointmentStatus.PENDING
        return try {
            appointmentRepository.saveAndFlush(appointment).toFilterResponse()
        } catch (_: DataIntegrityViolationException) {
            throw DuplicateResourceException("The selected appointment slot is already booked")
        }
    }

    private fun getAppointment(id: UUID) = appointmentRepository.findById(id).orElseThrow { ResourceNotFoundException("Appointment not found with ID: $id") }

    private fun Appointment.toFilterResponse() = AppointmentFilterResponse(
        appointmentId = id!!,
        patientId = patient!!.id!!,
        patientName = patient!!.fullName,
        clinicId = clinicRepository.findByUserId(clinic!!.id!!).orElseThrow { ResourceNotFoundException("Clinic not found") }.id!!,
        clinicName = clinicRepository.findByUserId(clinic!!.id!!).orElseThrow { ResourceNotFoundException("Clinic not found") }.clinicName,
        doctorId = doctor!!.id!!,
        doctorName = doctor!!.fullName,
        serviceId = service!!.id!!,
        serviceName = service!!.serviceName,
        appointmentAt = appointmentDate!!.atZone(zoneId).toOffsetDateTime(),
        status = status,
        createdAt = createdAt.atZone(zoneId).toOffsetDateTime()
    )
}