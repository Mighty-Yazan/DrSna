package com.example.demo.repository

import com.example.demo.model.Appointment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import com.example.demo.model.AppointmentStatus

@Repository
interface AppointmentRepository : JpaRepository<Appointment, UUID> {

    /*
     * Used to prevent two active appointments from occupying
     * the same doctor/time slot.
     */
    fun existsByBookingKey(bookingKey: String): Boolean

    /*
     * Used when rescheduling an existing appointment.
     * The current appointment itself must be ignored.
     */
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
    fun deleteAllByDoctor_Id(doctorId: UUID)

    // دالة حساب أعداد الحجوزات غير الملغاة بين تاريخين
    @Query("""
        SELECT COUNT(a) 
        FROM Appointment a 
        WHERE a.appointmentDate >= :startDate 
          AND a.appointmentDate <= :endDate 
          AND a.status != com.example.demo.model.AppointmentStatus.CANCELLED
    """)
    fun countAppointmentsBetweenDates(
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant
    ): Long

    // دالة حساب عدد الحجوزات المكتملة بين تاريخين
    @Query("""
        SELECT COUNT(a) 
        FROM Appointment a 
        WHERE a.appointmentDate >= :startDate 
          AND a.appointmentDate <= :endDate 
          AND a.status = com.example.demo.model.AppointmentStatus.COMPLETED
    """)
    fun countCompletedAppointmentsBetweenDates(
        @Param("startDate") startDate: Instant,
        @Param("endDate") endDate: Instant
    ): Long

    // أضيفي هذه الدالة في AppointmentRepository
    @Query("""
        SELECT a 
        FROM Appointment a 
        JOIN FETCH a.clinic c 
        WHERE a.appointmentDate >= :startDate 
          AND a.appointmentDate <= :endDate 
          AND a.status = com.example.demo.model.AppointmentStatus.COMPLETED
    """)
    fun findAllCompletedAppointmentsBetweenDates(
        @Param("startDate") startDate: java.time.Instant,
        @Param("endDate") endDate: java.time.Instant
    ): List<Appointment>

    fun findAllByStatus(status: AppointmentStatus): List<Appointment>
}
