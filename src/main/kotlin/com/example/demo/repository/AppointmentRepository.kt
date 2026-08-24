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
}
