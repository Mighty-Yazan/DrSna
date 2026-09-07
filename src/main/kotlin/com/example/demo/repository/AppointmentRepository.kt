package com.example.demo.repository

import com.example.demo.model.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface AppointmentRepository : JpaRepository<Appointment, UUID> {

    fun existsByBookingKey(bookingKey: String): Boolean

    fun existsByBookingKeyAndIdNot(
        bookingKey: String,
        id: UUID
    ): Boolean

    fun findAllByDoctorIdAndAppointmentDateBetween(
        doctorId: UUID,
        from: Instant,
        to: Instant
    ): List<Appointment>

    fun findAllByClinicIdAndAppointmentDateBetween(
        clinicId: UUID,
        from: Instant,
        to: Instant
    ): List<Appointment>

    fun findAllByPatient_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
        patientId: UUID,
        from: Instant
    ): List<Appointment>

    fun findAllByPatient_IdAndAppointmentDateLessThanOrderByAppointmentDateDesc(
        patientId: UUID,
        before: Instant
    ): List<Appointment>

    fun findAllByPatient_IdOrderByAppointmentDateAsc(
        patientId: UUID
    ): List<Appointment>

    fun findAllByDoctor_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
        doctorId: UUID,
        from: Instant
    ): List<Appointment>

    fun findAllByDoctor_IdAndAppointmentDateLessThanOrderByAppointmentDateDesc(
        doctorId: UUID,
        before: Instant
    ): List<Appointment>

    fun findAllByDoctor_IdOrderByAppointmentDateAsc(
        doctorId: UUID
    ): List<Appointment>

    fun findAllByClinic_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
        clinicId: UUID,
        from: Instant
    ): List<Appointment>

    fun findAllByAppointmentDateBetween(
        from: Instant,
        to: Instant
    ): List<Appointment>

    /*
     * Delete all appointments belonging to a specific clinic.
     *
     * Used when permanently removing a clinic.
     */
    fun deleteAllByClinic_Id(
        clinicId: UUID
    )

    /*
     * Delete all appointments belonging to a specific doctor.
     *
     * Used when permanently removing a doctor
     * after the doctor is no longer associated
     * with any clinic.
     */
    fun deleteAllByDoctor_Id(
        doctorId: UUID
    )
}
