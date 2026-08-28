package com.example.demo.service

import com.example.demo.dto.AppointmentListResponse
import com.example.demo.dto.AppointmentSummaryResponse
import com.example.demo.dto.MessageResponse
import com.example.demo.dto.RescheduleAppointmentRequest
import com.example.demo.dto.UpdateAppointmentStatusRequest
import com.example.demo.exception.AppException
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.Appointment
import com.example.demo.model.AppointmentStatus
import com.example.demo.model.ScheduleType
import com.example.demo.repository.AppointmentRepository
import com.example.demo.repository.ClinicDoctorRepository
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.ScheduleRepository
import com.example.demo.repository.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Service
class AppointmentService(
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository,
    private val scheduleRepository: ScheduleRepository
) {

    private val zoneId = ZoneId.of("Asia/Amman")

    // ============================================================
    // PATIENT
    // ============================================================

    @Transactional(readOnly = true)
    fun getMyAppointments(
        patientEmail: String,
        scope: String
    ): AppointmentListResponse {

        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow {
                ResourceNotFoundException(
                    "Authenticated patient was not found"
                )
            }

        val normalizedScope =
            scope.trim().lowercase().ifEmpty {
                "upcoming"
            }

        if (
            normalizedScope !in
            listOf("upcoming", "past", "all")
        ) {
            throw AppException(
                "Invalid scope '$scope'. Allowed values are: upcoming, past, all"
            )
        }

        val now = Instant.now()

        val appointments =
            when (normalizedScope) {

                "upcoming" ->
                    appointmentRepository
                        .findAllByPatient_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                            patient.id!!,
                            now
                        )

                "past" ->
                    appointmentRepository
                        .findAllByPatient_IdAndAppointmentDateLessThanOrderByAppointmentDateDesc(
                            patient.id!!,
                            now
                        )

                else ->
                    appointmentRepository
                        .findAllByPatient_IdOrderByAppointmentDateAsc(
                            patient.id!!
                        )
            }

        return AppointmentListResponse(
            scope = normalizedScope,
            count = appointments.size,
            appointments = appointments.map {
                it.toSummary()
            }
        )
    }

    @Transactional(readOnly = true)
    fun getAppointmentForPatient(
        patientEmail: String,
        appointmentId: UUID
    ): AppointmentSummaryResponse {

        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow {
                ResourceNotFoundException(
                    "Authenticated patient was not found"
                )
            }

        val appointment =
            appointmentRepository.findById(appointmentId)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Appointment not found with ID: $appointmentId"
                    )
                }

        if (appointment.patient?.id != patient.id) {
            throw ResourceNotFoundException(
                "Appointment not found with ID: $appointmentId"
            )
        }

        return appointment.toSummary()
    }

    @Transactional
    fun cancelAppointment(
        patientEmail: String,
        appointmentId: UUID
    ): MessageResponse {

        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow {
                ResourceNotFoundException(
                    "Authenticated patient was not found"
                )
            }

        val appointment =
            appointmentRepository.findById(appointmentId)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Appointment not found with ID: $appointmentId"
                    )
                }

        if (appointment.patient?.id != patient.id) {
            throw ResourceNotFoundException(
                "Appointment not found with ID: $appointmentId"
            )
        }

        ensureCancellable(appointment)

        appointment.status = AppointmentStatus.CANCELLED

        // Free the slot while keeping the appointment history.
        appointment.bookingKey = null

        appointmentRepository.save(appointment)

        return MessageResponse(
            "Appointment cancelled successfully"
        )
    }

    // ============================================================
    // DOCTOR
    // ============================================================

    @Transactional(readOnly = true)
    fun getDoctorAppointments(
        doctorEmail: String,
        date: LocalDate?
    ): List<AppointmentSummaryResponse> {

        val doctor = userRepository.findByEmail(doctorEmail)
            .orElseThrow {
                ResourceNotFoundException(
                    "Authenticated doctor was not found"
                )
            }

        val appointments =
            if (date != null) {

                requireDateNotPast(date)

                val from =
                    date.atStartOfDay(zoneId).toInstant()

                val to =
                    date.plusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()

                appointmentRepository
                    .findAllByDoctorIdAndAppointmentDateBetween(
                        doctor.id!!,
                        from,
                        to
                    )
                    .sortedBy {
                        it.appointmentDate
                    }

            } else {

                appointmentRepository
                    .findAllByDoctor_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                        doctor.id!!,
                        Instant.now()
                    )
            }

        return appointments.map {
            it.toSummary()
        }
    }

    // ============================================================
    // CLINIC
    // ============================================================

    @Transactional(readOnly = true)
    fun getClinicAppointments(
        clinicEmail: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        doctorId: UUID?,
        status: AppointmentStatus? = null
    ): List<AppointmentSummaryResponse> {

        val clinic =
            clinicRepository.findByUserEmail(clinicEmail)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Clinic profile not found for authenticated clinic"
                    )
                }

        val clinicUserId =
            clinic.user!!.id!!

        var appointments =
            if (startDate != null && endDate != null) {
                val from = startDate.atStartOfDay(zoneId).toInstant()
                val to = endDate.plusDays(1).atStartOfDay(zoneId).toInstant()

                appointmentRepository
                    .findAllByClinicIdAndAppointmentDateBetween(
                        clinicUserId,
                        from,
                        to
                    )
                    .sortedBy {
                        it.appointmentDate
                    }
            } else if (startDate != null) {
                val from = startDate.atStartOfDay(zoneId).toInstant()
                val to = startDate.plusDays(1).atStartOfDay(zoneId).toInstant()

                appointmentRepository
                    .findAllByClinicIdAndAppointmentDateBetween(
                        clinicUserId,
                        from,
                        to
                    )
                    .sortedBy {
                        it.appointmentDate
                    }
            } else {

                appointmentRepository
                    .findAllByClinic_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                        clinicUserId,
                        Instant.now()
                    )
            }

        if (doctorId != null) {

            if (
                !clinicDoctorRepository
                    .existsByClinic_IdAndDoctor_Id(
                        clinicUserId,
                        doctorId
                    )
            ) {
                throw ResourceNotFoundException(
                    "Doctor is not associated with this clinic"
                )
            }

            appointments =
                appointments.filter {
                    it.doctor?.id == doctorId
                }
        }

        if (status != null) {
            appointments =
                appointments.filter {
                    it.status == status
                }
        }

        return appointments.map {
            it.toSummary()
        }
    }

    @Transactional
    fun createWalkInAppointment(
        clinicEmail: String,
        request: com.example.demo.dto.CreateWalkInAppointmentRequest
    ): AppointmentSummaryResponse {

        val clinic =
            clinicRepository.findByUserEmail(clinicEmail)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Clinic profile not found for authenticated clinic"
                    )
                }

        val clinicUserId = clinic.user!!.id!!
        val doctorId = request.doctorId ?: throw AppException("Doctor ID is required")
        val date = request.appointmentDate ?: throw AppException("Appointment date is required")
        val time = request.appointmentTime ?: throw AppException("Appointment time is required")

        if (
            !clinicDoctorRepository
                .existsByClinic_IdAndDoctor_Id(
                    clinicUserId,
                    doctorId
                )
        ) {
            throw ResourceNotFoundException(
                "Doctor is not associated with this clinic"
            )
        }

        val doctorUser = userRepository.findById(doctorId).orElseThrow {
            ResourceNotFoundException("Doctor not found")
        }

        val appointment = Appointment(
            clinic = clinic.user!!,
            doctor = doctorUser,
            patient = null,
            service = null,
            schedule = null,
            appointmentDate = date.atTime(time).atZone(zoneId).toInstant(),
            status = AppointmentStatus.CONFIRMED, // Walk-in is automatically confirmed
            bookingKey = null
        )

        validateAppointmentSlot(appointment, date, time)

        val newInstant = appointment.appointmentDate!!
        val newBookingKey = buildBookingKey(doctorUser.id!!, newInstant)

        if (appointmentRepository.existsByBookingKey(newBookingKey)) {
            throw DuplicateResourceException("The selected appointment slot is already booked")
        }

        appointment.bookingKey = newBookingKey

        return saveSafely(appointment).toSummary()
    }

    @Transactional
    fun deleteWalkInAppointment(
        clinicEmail: String,
        appointmentId: UUID
    ): MessageResponse {
        val appointment = findClinicAppointment(clinicEmail, appointmentId)

        appointmentRepository.delete(appointment)

        return MessageResponse("Appointment deleted successfully")
    }

    // ============================================================
    // GENERIC APPOINTMENT LIST
    // ============================================================

    @Transactional(readOnly = true)
    fun getAppointments(
        email: String,
        role: String,
        status: AppointmentStatus?,
        doctorId: UUID?,
        date: LocalDate?
    ): List<AppointmentSummaryResponse> {

        return when (role) {

            "CLINIC" ->
                getClinicAppointments(
                    email,
                    date,
                    null, // endDate
                    doctorId,
                    status
                )

            "DOCTOR" -> {

                val doctor =
                    userRepository.findByEmail(email)
                        .orElseThrow {
                            ResourceNotFoundException(
                                "Authenticated doctor was not found"
                            )
                        }

                if (
                    doctorId != null &&
                    doctorId != doctor.id
                ) {
                    throw AppException(
                        "Doctors can only view their own appointments"
                    )
                }

                getDoctorAppointmentsFiltered(
                    doctor.id!!,
                    status,
                    date
                )
            }

            "PATIENT" -> {

                val patient =
                    userRepository.findByEmail(email)
                        .orElseThrow {
                            ResourceNotFoundException(
                                "Authenticated patient was not found"
                            )
                        }

                getPatientAppointmentsFiltered(
                    patient.id!!,
                    status,
                    date
                )
                    .filter {
                        doctorId == null ||
                                it.doctor?.id == doctorId
                    }
                    .map {
                        it.toSummary()
                    }
            }

            else ->
                throw AppException(
                    "This role is not allowed to view appointments"
                )
        }
    }

    // ============================================================
    // UPDATE STATUS
    // ============================================================

    @Transactional
    fun updateStatus(
        clinicEmail: String,
        appointmentId: UUID,
        request: UpdateAppointmentStatusRequest
    ): AppointmentSummaryResponse {

        val appointment =
            findClinicAppointment(
                clinicEmail,
                appointmentId
            )

        val newStatus =
            request.status

        validateStatusTransition(
            appointment.status,
            newStatus
        )

        if (
            newStatus ==
            AppointmentStatus.CANCELLED
        ) {
            appointment.bookingKey = null
        }

        appointment.status =
            newStatus

        return saveSafely(
            appointment
        ).toSummary()
    }

    // ============================================================
    // RESCHEDULE
    // ============================================================

    @Transactional
    fun reschedule(
        clinicEmail: String,
        appointmentId: UUID,
        request: RescheduleAppointmentRequest
    ): AppointmentSummaryResponse {

        val appointment =
            findClinicAppointment(
                clinicEmail,
                appointmentId
            )

        if (
            appointment.status ==
            AppointmentStatus.CANCELLED
        ) {
            throw AppException(
                "Cancelled appointments cannot be rescheduled"
            )
        }

        if (
            appointment.status ==
            AppointmentStatus.COMPLETED
        ) {
            throw AppException(
                "Completed appointments cannot be rescheduled"
            )
        }

        val newDate =
            request.appointmentDate
                ?: throw AppException(
                    "Appointment date is required"
                )

        val newTime =
            request.appointmentTime
                ?: throw AppException(
                    "Appointment time is required"
                )

        validateAppointmentSlot(
            appointment,
            newDate,
            newTime
        )

        val newInstant =
            newDate
                .atTime(newTime)
                .atZone(zoneId)
                .toInstant()

        val newBookingKey =
            buildBookingKey(
                appointment.doctor!!.id!!,
                newInstant
            )

        if (
            newBookingKey != appointment.bookingKey &&
            appointmentRepository
                .existsByBookingKeyAndIdNot(
                    newBookingKey,
                    appointment.id!!
                )
        ) {
            throw DuplicateResourceException(
                "The selected appointment slot is already booked"
            )
        }

        appointment.appointmentDate =
            newInstant

        appointment.bookingKey =
            newBookingKey

        return saveSafely(
            appointment
        ).toSummary()
    }

    // ============================================================
    // FILTER HELPERS
    // ============================================================

    private fun getDoctorAppointmentsFiltered(
        doctorId: UUID,
        status: AppointmentStatus?,
        date: LocalDate?
    ): List<AppointmentSummaryResponse> {

        val appointments =
            if (date != null) {

                requireDateNotPast(date)

                val from =
                    date.atStartOfDay(zoneId).toInstant()

                val to =
                    date.plusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()

                appointmentRepository
                    .findAllByDoctorIdAndAppointmentDateBetween(
                        doctorId,
                        from,
                        to
                    )

            } else {

                appointmentRepository
                    .findAllByDoctor_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                        doctorId,
                        Instant.now()
                    )
            }

        return appointments
            .filter {
                status == null ||
                        it.status == status
            }
            .sortedBy {
                it.appointmentDate
            }
            .map {
                it.toSummary()
            }
    }

    private fun getPatientAppointmentsFiltered(
        patientId: UUID,
        status: AppointmentStatus?,
        date: LocalDate?
    ): List<Appointment> {

        val appointments =
            if (date != null) {

                requireDateNotPast(date)

                val from =
                    date.atStartOfDay(zoneId).toInstant()

                val to =
                    date.plusDays(1)
                        .atStartOfDay(zoneId)
                        .toInstant()

                appointmentRepository
                    .findAllByPatient_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                        patientId,
                        from
                    )
                    .filter {
                        it.appointmentDate!! < to
                    }

            } else {

                appointmentRepository
                    .findAllByPatient_IdOrderByAppointmentDateAsc(
                        patientId
                    )
            }

        return appointments
            .filter {
                status == null ||
                        it.status == status
            }
            .sortedBy {
                it.appointmentDate
            }
    }

    // ============================================================
    // SECURITY / OWNERSHIP
    // ============================================================

    private fun findClinicAppointment(
        clinicEmail: String,
        appointmentId: UUID
    ): Appointment {

        val clinic =
            clinicRepository.findByUserEmail(clinicEmail)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Clinic profile not found for authenticated clinic"
                    )
                }

        val appointment =
            appointmentRepository.findById(appointmentId)
                .orElseThrow {
                    ResourceNotFoundException(
                        "Appointment not found with ID: $appointmentId"
                    )
                }

        if (
            appointment.clinic?.id !=
            clinic.user?.id
        ) {
            throw ResourceNotFoundException(
                "Appointment not found with ID: $appointmentId"
            )
        }

        return appointment
    }

    // ============================================================
    // STATUS TRANSITIONS
    // ============================================================

    private fun validateStatusTransition(
        current: AppointmentStatus,
        target: AppointmentStatus
    ) {

        if (current == target) {
            return
        }

        val allowed =
            when (current) {

                AppointmentStatus.PENDING ->
                    setOf(
                        AppointmentStatus.CONFIRMED,
                        AppointmentStatus.CANCELLED
                    )

                AppointmentStatus.CONFIRMED ->
                    setOf(
                        AppointmentStatus.COMPLETED,
                        AppointmentStatus.CANCELLED
                    )

                AppointmentStatus.CANCELLED ->
                    emptySet()

                AppointmentStatus.COMPLETED ->
                    emptySet()
            }

        if (target !in allowed) {
            throw AppException(
                "Invalid appointment status transition: $current -> $target"
            )
        }
    }

    // ============================================================
    // CANCELLATION
    // ============================================================

    private fun ensureCancellable(
        appointment: Appointment
    ) {

        if (
            appointment.status ==
            AppointmentStatus.CANCELLED
        ) {
            throw AppException(
                "Appointment is already cancelled"
            )
        }

        if (
            appointment.status ==
            AppointmentStatus.COMPLETED
        ) {
            throw AppException(
                "Completed appointments cannot be cancelled"
            )
        }

        val appointmentDate =
            appointment.appointmentDate
                ?: throw AppException(
                    "Appointment has no scheduled date"
                )

        if (
            !appointmentDate.isAfter(
                Instant.now()
            )
        ) {
            throw AppException(
                "Appointment cannot be cancelled because it already started"
            )
        }
    }

    // ============================================================
    // RESCHEDULE VALIDATION
    // ============================================================

    private fun validateAppointmentSlot(
        appointment: Appointment,
        date: LocalDate,
        time: LocalTime
    ) {

        requireDateNotPast(date)

        if (
            time.minute != 0 ||
            time.second != 0 ||
            time.nano != 0
        ) {
            throw AppException(
                "Appointments must start on a 60-minute slot (top of the hour)"
            )
        }

        val clinicUserId =
            appointment.clinic?.id
                ?: throw AppException(
                    "Appointment has no clinic"
                )

        val doctorId =
            appointment.doctor?.id
                ?: throw AppException(
                    "Appointment has no doctor"
                )

        val clinic =
            clinicRepository.findByUserId(
                clinicUserId
            )
                .orElseThrow {
                    ResourceNotFoundException(
                        "Clinic not found"
                    )
                }

        val clinicId =
            clinic.id!!

        val isClinicHoliday =
            scheduleRepository
                .existsByClinicIdAndSpecificDateAndType(
                    clinicId,
                    date,
                    ScheduleType.HOLIDAY
                )

        if (isClinicHoliday) {
            throw AppException(
                "The clinic is closed on the selected date"
            )
        }

        val schedules =
            scheduleRepository
                .findByClinicId(clinicId)

        val isDoctorHoliday =
            schedules.any {
                it.doctor?.id == doctorId &&
                        it.type ==
                        ScheduleType.HOLIDAY &&
                        it.specificDate == date
            }

        if (isDoctorHoliday) {
            throw AppException(
                "The doctor is unavailable on the selected date"
            )
        }

        val doctorSchedule =
            schedules.firstOrNull {
                it.doctor?.id == doctorId &&
                        it.type ==
                        ScheduleType.DOCTOR_SHIFT &&
                        it.specificDate == date
            }
                ?: throw AppException(
                    "Doctor is not scheduled to work on this day"
                )

        val start = doctorSchedule.startTime!!
        val end = doctorSchedule.endTime!!

        if (
            !isValidSlot(
                start,
                end,
                time
            )
        ) {
            throw AppException(
                "Selected time is outside the doctor's available working hours"
            )
        }

        val newInstant =
            date
                .atTime(time)
                .atZone(zoneId)
                .toInstant()

        if (
            !newInstant.isAfter(
                Instant.now()
            )
        ) {
            throw AppException(
                "Appointment time must be in the future"
            )
        }
    }

    private fun isValidSlot(
        start: LocalTime,
        end: LocalTime,
        time: LocalTime
    ): Boolean {

        return !time.isBefore(start) &&
                !time.plusMinutes(60).isAfter(end) &&
                time.minute == 0 &&
                time.second == 0 &&
                time.nano == 0
    }

    // ============================================================
    // SAVE
    // ============================================================

    private fun saveSafely(
        appointment: Appointment
    ): Appointment {

        return try {

            appointmentRepository
                .saveAndFlush(appointment)

        } catch (
            e: DataIntegrityViolationException
        ) {
            val msg = e.mostSpecificCause.message ?: e.message ?: ""
            if (msg.contains("booking_key", ignoreCase = true)) {
                throw DuplicateResourceException(
                    "The selected appointment slot is already booked"
                )
            }
            throw AppException("Failed to save appointment: $msg")
        }
    }

    private fun buildBookingKey(
        doctorId: UUID,
        appointmentAt: Instant
    ): String =
        "$doctorId:$appointmentAt"

    // ============================================================
    // DATE
    // ============================================================

    private fun requireDateNotPast(
        date: LocalDate
    ) {

        if (
            date.isBefore(
                LocalDate.now(zoneId)
            )
        ) {
            throw AppException(
                "Date cannot be in the past"
            )
        }
    }

    // ============================================================
    // RESPONSE MAPPING
    // ============================================================

    private fun Appointment.toSummary():
            AppointmentSummaryResponse {

        val clinicUser =
            clinic!!

        val clinicEntity =
            clinicRepository
                .findByUserId(
                    clinicUser.id!!
                )
                .orElse(null)

        return AppointmentSummaryResponse(

            appointmentId =
                id!!,

            clinicId =
                service?.clinic?.id
                    ?: clinicEntity?.id
                    ?: clinicUser.id!!,

            clinicName =
                clinicEntity?.clinicName
                    ?: clinicUser.fullName,

            doctorId =
                doctor!!.id!!,

            doctorName =
                doctor!!.fullName,

            patientId =
                patient?.id,

            patientName =
                patient?.fullName,

            serviceId =
                service?.id,

            serviceName =
                service?.serviceName,

            scheduleId =
                schedule?.id,

            appointmentAt =
                appointmentDate!!
                    .atZone(zoneId)
                    .toOffsetDateTime(),

            createdAt =
                createdAt
                    .atZone(zoneId)
                    .toOffsetDateTime(),

            status =
                status
        )
    }
}