package com.example.demo.service

import com.example.demo.dto.AppointmentListResponse
import com.example.demo.dto.AppointmentSummaryResponse
import com.example.demo.dto.MessageResponse
import com.example.demo.exception.AppException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.Appointment
import com.example.demo.repository.AppointmentRepository
import com.example.demo.repository.ClinicDoctorRepository
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository
) {
    private val zoneId = ZoneId.of("Asia/Amman")

    @Transactional(readOnly = true)
    fun getMyAppointments(patientEmail: String, scope: String): AppointmentListResponse {
        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow { ResourceNotFoundException("Authenticated patient was not found") }

        val normalizedScope = scope.trim().lowercase().ifEmpty { "upcoming" }
        if (normalizedScope !in listOf("upcoming", "past", "all")) {
            throw AppException("Invalid scope '$scope'. Allowed values are: upcoming, past, all")
        }

        val now = Instant.now()
        val appointments = when (normalizedScope) {
            "upcoming" -> appointmentRepository.findAllByPatient_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(patient.id!!, now)
            "past" -> appointmentRepository.findAllByPatient_IdAndAppointmentDateLessThanOrderByAppointmentDateDesc(patient.id!!, now)
            else -> appointmentRepository.findAllByPatient_IdOrderByAppointmentDateAsc(patient.id!!)
        }

        return AppointmentListResponse(
            scope = normalizedScope,
            count = appointments.size,
            appointments = appointments.map { it.toSummary() }
        )
    }

    @Transactional(readOnly = true)
    fun getAppointmentForPatient(patientEmail: String, appointmentId: UUID): AppointmentSummaryResponse {
        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow { ResourceNotFoundException("Authenticated patient was not found") }

        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { ResourceNotFoundException("Appointment not found with ID: $appointmentId") }

        if (appointment.patient?.id != patient.id) {
            throw ResourceNotFoundException("Appointment not found with ID: $appointmentId")
        }

        return appointment.toSummary()
    }

    @Transactional
    fun cancelAppointment(patientEmail: String, appointmentId: UUID): MessageResponse {
        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow { ResourceNotFoundException("Authenticated patient was not found") }

        val appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow { ResourceNotFoundException("Appointment not found with ID: $appointmentId") }

        if (appointment.patient?.id != patient.id) {
            throw ResourceNotFoundException("Appointment not found with ID: $appointmentId")
        }

        val appointmentDate = appointment.appointmentDate
            ?: throw AppException("Appointment has no scheduled date")

        if (!appointmentDate.isAfter(Instant.now())) {
            throw AppException("Appointment cannot be cancelled because it already started")
        }

        appointmentRepository.delete(appointment)
        return MessageResponse("Appointment cancelled successfully")
    }

    @Transactional(readOnly = true)
    fun getDoctorAppointments(doctorEmail: String, date: LocalDate?): List<AppointmentSummaryResponse> {
        val doctor = userRepository.findByEmail(doctorEmail)
            .orElseThrow { ResourceNotFoundException("Authenticated doctor was not found") }

        val appointments = if (date != null) {
            requireDateNotPast(date)
            val from = date.atStartOfDay(zoneId).toInstant()
            val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctor.id!!, from, to)
                .sortedBy { it.appointmentDate }
        } else {
            appointmentRepository.findAllByDoctor_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(doctor.id!!, Instant.now())
        }

        return appointments.map { it.toSummary() }
    }

    @Transactional(readOnly = true)
    fun getClinicAppointments(clinicEmail: String, date: LocalDate?, doctorId: UUID?): List<AppointmentSummaryResponse> {
        val clinic = clinicRepository.findByUserEmail(clinicEmail)
            .orElseThrow { ResourceNotFoundException("Clinic profile not found for authenticated clinic") }
        val clinicUserId = clinic.user!!.id!!

        var appointments = if (date != null) {
            requireDateNotPast(date)
            val from = date.atStartOfDay(zoneId).toInstant()
            val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            appointmentRepository.findAllByClinicIdAndAppointmentDateBetween(clinicUserId, from, to)
                .sortedBy { it.appointmentDate }
        } else {
            appointmentRepository.findAllByClinic_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(clinicUserId, Instant.now())
        }

        if (doctorId != null) {
            if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinicUserId, doctorId)) {
                throw ResourceNotFoundException("Doctor is not associated with this clinic")
            }
            appointments = appointments.filter { it.doctor?.id == doctorId }
        }

        return appointments.map { it.toSummary() }
    }

    private fun requireDateNotPast(date: LocalDate) {
        if (date.isBefore(LocalDate.now(zoneId))) {
            throw AppException("Date cannot be in the past")
        }
    }

    private fun Appointment.toSummary(): AppointmentSummaryResponse {
        val clinicUser = clinic!!
        val clinicEntity = clinicRepository.findByUserId(clinicUser.id!!).orElse(null)

        return AppointmentSummaryResponse(
            appointmentId = id!!,
            clinicId = service?.clinic?.id ?: clinicEntity?.id ?: clinicUser.id!!,
            clinicName = clinicEntity?.clinicName ?: clinicUser.fullName,
            doctorId = doctor!!.id!!,
            doctorName = doctor!!.fullName,
            patientId = patient!!.id!!,
            patientName = patient!!.fullName,
            serviceId = service!!.id!!,
            serviceName = service!!.serviceName,
            scheduleId = schedule?.id,
            appointmentAt = appointmentDate!!.atZone(zoneId).toOffsetDateTime(),
            createdAt = createdAt.atZone(zoneId).toOffsetDateTime()
        )
    }
}
