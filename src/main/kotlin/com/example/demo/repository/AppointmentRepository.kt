package com.example.demo.repository

import com.example.demo.model.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AppointmentRepository : JpaRepository<Appointment, UUID> {
    fun existsByDoctorIdAndAppointmentDate(doctorId: UUID, appointmentDate: Instant): Boolean
    fun findAllByDoctorIdAndAppointmentDateBetween(doctorId: UUID, from: Instant, to: Instant): List<Appointment>
    fun findAllByClinicIdAndAppointmentDateBetween(clinicId: UUID, from: Instant, to: Instant): List<Appointment>

    fun findAllByPatient_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(patientId: UUID, from: Instant): List<Appointment>
    fun findAllByPatient_IdAndAppointmentDateLessThanOrderByAppointmentDateDesc(patientId: UUID, before: Instant): List<Appointment>
    fun findAllByPatient_IdOrderByAppointmentDateAsc(patientId: UUID): List<Appointment>
    fun findAllByDoctor_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(doctorId: UUID, from: Instant): List<Appointment>
    fun findAllByClinic_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(clinicId: UUID, from: Instant): List<Appointment>
}
