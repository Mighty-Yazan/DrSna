package com.example.demo.service

import com.example.demo.dto.AppointmentListResponse
import com.example.demo.dto.AppointmentSummaryResponse
import com.example.demo.dto.BookAppointmentRequest
import com.example.demo.dto.BookAppointmentResponse
import com.example.demo.dto.MessageResponse
import com.example.demo.dto.RescheduleAppointmentRequest
import com.example.demo.dto.UpdateAppointmentStatusRequest
import com.example.demo.exception.AppException
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.Appointment
import com.example.demo.model.AppointmentStatus
import com.example.demo.model.Schedule
import com.example.demo.model.ScheduleType
import com.example.demo.repository.AppointmentRepository
import com.example.demo.repository.ClinicDoctorRepository
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.ScheduleRepository
import com.example.demo.repository.SpecialtyRepository
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
    private val scheduleRepository: ScheduleRepository,
    private val specialtyRepository: SpecialtyRepository,
    private val clinicSpecialtyRepository: com.example.demo.repository.ClinicSpecialtyRepository

) {

    private val zoneId = ZoneId.of("UTC")

    // ============================================================
    // DOHA — APPOINTMENT FORM BOOKING
    // POST /api/appointments/book
    // ============================================================

    /**
     * Receives booking data from the Frontend Appointment Form and saves it.
     * Fields: patientName, patientAge, serviceId, appointmentAt, paymentMethod.
     * Returns a confirmation response with the generated appointmentId.
     */
    @Transactional
    fun bookAppointment(authentication: org.springframework.security.core.Authentication?, request: BookAppointmentRequest): BookAppointmentResponse {
        if (request.patientAge < 6) {
            throw AppException("Appointments cannot be booked for patients under 6 years old")
        }

        val date = request.appointmentAt.toLocalDate()
        val time = request.appointmentAt.toLocalTime()

        // 1. Validate that appointmentAt is in the future
        val appointmentInstant = request.appointmentAt
            .atZone(zoneId)
            .toInstant()

        if (!appointmentInstant.isAfter(Instant.now())) {
            throw AppException("Appointment date and time must be in the future")
        }

        // 2. Fetch dependencies
        val clinicEntity = clinicRepository.findById(request.clinicId)
            .orElseThrow { ResourceNotFoundException("Clinic with ID '${request.clinicId}' was not found") }
        val clinicUser = clinicEntity.user!!

        val doctorUser = userRepository.findById(request.doctorId)
            .orElseThrow { ResourceNotFoundException("Doctor with ID '${request.doctorId}' was not found") }

        val serviceIds = request.serviceIds.distinct()
        val specialtiesList = specialtyRepository.findAllById(serviceIds)
        if (specialtiesList.size != serviceIds.size) {
            throw ResourceNotFoundException("One or more selected services were not found")
        }

        val clinicServiceRelations = clinicSpecialtyRepository
            .findAllByClinicId(request.clinicId)
            .filter { it.specialty?.id in serviceIds }

        if (clinicServiceRelations.size != serviceIds.size) {
            throw AppException("One or more selected services are not available at this clinic")
        }

        val totalDurationMinutes = clinicServiceRelations.sumOf { it.durationMinutes }
        if (totalDurationMinutes <= 0) {
            throw AppException("Selected service duration must be greater than zero")
        }

        val patientUser = authentication?.name?.let { email ->
            userRepository.findByEmail(email).orElse(null)
        }

        // 3. Build booking key & check for duplicate slot upfront
        val bookingKey = buildBookingKey(doctorUser.id!!, appointmentInstant)
        if (appointmentRepository.existsByBookingKey(bookingKey)) {
            throw DuplicateResourceException(
                "The selected appointment slot has already been booked. Please choose another time."
            )
        }

        if (time.minute % 15 != 0 || time.second != 0 || time.nano != 0) {
            throw AppException("Appointments must start on a 15-minute slot")
        }

        val from = date.atStartOfDay(zoneId).toInstant()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val existingAppointments = appointmentRepository
            .findAllByDoctorIdAndAppointmentDateBetween(request.doctorId, from, to)
            .filter { it.status != AppointmentStatus.CANCELLED }

        if (hasAppointmentOverlap(existingAppointments, appointmentInstant, totalDurationMinutes)) {
            throw DuplicateResourceException(
                "The selected appointment time overlaps with an existing appointment"
            )
        }

        // 4. Build Appointment entity
        val appointment = Appointment(
            clinic          = clinicUser,
            doctor          = doctorUser,
            patient         = patientUser,
            formPatientName = request.patientName,
            formPatientAge  = request.patientAge,
            specialties     = specialtiesList.toMutableList(),
            appointmentDate = appointmentInstant,
            durationMinutes = totalDurationMinutes,
            paymentMethod   = request.paymentMethod,
            status          = AppointmentStatus.PENDING,
            bookingKey      = bookingKey
        )

        val doctorSchedule = this.validateAppointmentSlot(appointment, date, time)
        appointment.schedule = doctorSchedule

        // 5. Use saveSafely to handle concurrency/race conditions gracefully
        val saved = saveSafely(appointment)

        // 6. Return confirmation response
        return BookAppointmentResponse(
            appointmentId  = saved.id!!,
            message        = "Appointment Created Successfully",
            patientName    = request.patientName,
            patientAge     = request.patientAge,
            serviceNames   = specialtiesList.map { it.name },
            appointmentAt  = request.appointmentAt
                .atZone(zoneId)
                .toOffsetDateTime(),
            paymentMethod  = request.paymentMethod,
            status         = saved.status
        )
    }
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
        date: LocalDate?,
        scope: String = "upcoming"
    ): List<AppointmentSummaryResponse> {

        val doctor = userRepository.findByEmail(doctorEmail)
            .orElseThrow {
                ResourceNotFoundException(
                    "Authenticated doctor was not found"
                )
            }

        // A specific date takes priority over scope (used by the doctor's day view)
        if (date != null) {

            requireDateNotPast(date)

            val from =
                date.atStartOfDay(zoneId).toInstant()

            val to =
                date.plusDays(1)
                    .atStartOfDay(zoneId)
                    .toInstant()

            return appointmentRepository
                .findAllByDoctorIdAndAppointmentDateBetween(
                    doctor.id!!,
                    from,
                    to
                )
                .sortedBy {
                    it.appointmentDate
                }
                .map {
                    it.toSummary()
                }
        }

        // ── Appointments (upcoming) / History Appointment (past) / all ──
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
                        .findAllByDoctor_IdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                            doctor.id!!,
                            now
                        )

                "past" ->
                    appointmentRepository
                        .findAllByDoctor_IdAndAppointmentDateLessThanOrderByAppointmentDateDesc(
                            doctor.id!!,
                            now
                        )

                else ->
                    appointmentRepository
                        .findAllByDoctor_IdOrderByAppointmentDateAsc(
                            doctor.id!!
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
            specialties = mutableListOf(),
            schedule = null,
            appointmentDate = date.atTime(time).atZone(zoneId).toInstant(),
            durationMinutes = 15,
            status = AppointmentStatus.CONFIRMED, // Walk-in is automatically confirmed
            bookingKey = null
        )

        validateAppointmentSlot(appointment, date, time)

        val newInstant = appointment.appointmentDate!!
        val newBookingKey = buildBookingKey(doctorUser.id!!, newInstant)

        if (appointmentRepository.existsByBookingKey(newBookingKey)) {
            throw DuplicateResourceException("The selected appointment slot is already booked")
        }

        val from = date.atStartOfDay(zoneId).toInstant()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val existingAppointments = appointmentRepository
            .findAllByDoctorIdAndAppointmentDateBetween(doctorUser.id!!, from, to)
            .filter { it.status != AppointmentStatus.CANCELLED }

        if (hasAppointmentOverlap(existingAppointments, newInstant, appointment.durationMinutes, appointment.id)) {
            throw DuplicateResourceException(
                "The selected appointment time overlaps with an existing appointment"
            )
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

        val from = newDate.atStartOfDay(zoneId).toInstant()
        val to = newDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        val existingAppointments = appointmentRepository
            .findAllByDoctorIdAndAppointmentDateBetween(
                appointment.doctor!!.id!!,
                from,
                to
            )
            .filter { it.status != AppointmentStatus.CANCELLED }

        if (hasAppointmentOverlap(
                existingAppointments,
                newInstant,
                appointment.durationMinutes,
                appointment.id
            )
        ) {
            throw DuplicateResourceException(
                "The selected appointment time overlaps with an existing appointment"
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
    ): Schedule {

        requireDateNotPast(date)

        if (
            time.minute % 15 != 0 ||
            time.second != 0 ||
            time.nano != 0
        ) {
            throw AppException(
                "Appointments must start on a 15-minute slot"
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

        // Fetch all schedules for this clinic using existing ScheduleRepository method
        val schedules =
            scheduleRepository
                .findByClinicId(clinicId)

        // ── 1. Check if Clinic is Open on this Day of the Week (CLINIC_HOURS) ──
        val dayOfWeek = date.dayOfWeek
        val clinicDaySchedule = schedules.firstOrNull {
            it.type == ScheduleType.CLINIC_HOURS && it.dayOfWeek == dayOfWeek
        }

        if (clinicDaySchedule == null || clinicDaySchedule.startTime == null || clinicDaySchedule.endTime == null) {
            val dayName = dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
            throw AppException("The clinic is closed on ${dayName}s")
        }

        // Validate time is within clinic opening hours
        if (
            time.isBefore(clinicDaySchedule.startTime) ||
            time.plusMinutes(appointment.durationMinutes.toLong()).isAfter(clinicDaySchedule.endTime)
        ) {
            throw AppException("Selected time is outside the clinic's operating hours (${clinicDaySchedule.startTime} - ${clinicDaySchedule.endTime})")
        }

        // ── 2. Check Clinic Specific Holiday ──
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

        // ── 3. Check Doctor Holiday ──
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

        // ── 4. Check Doctor Shift (Date-specific or Recurring Day) ──
        val doctorSchedule =
            schedules.firstOrNull {
                it.doctor?.id == doctorId &&
                        it.type == ScheduleType.DOCTOR_SHIFT &&
                        (it.specificDate == date || (it.specificDate == null && it.dayOfWeek == dayOfWeek))
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
                time,
                appointment.durationMinutes
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
                "Appointment date and time must be in the future"
            )
        }

        return doctorSchedule
    }
    private fun isValidSlot(
        start: LocalTime,
        end: LocalTime,
        time: LocalTime,
        durationMinutes: Int
    ): Boolean {

        return !time.isBefore(start) &&
                time.minute % 15 == 0 &&
                time.second == 0 &&
                time.nano == 0 &&
                !time.plusMinutes(durationMinutes.toLong()).isAfter(end)
    }

    private fun hasAppointmentOverlap(
        appointments: List<Appointment>,
        newStart: Instant,
        newDurationMinutes: Int,
        ignoreAppointmentId: UUID? = null
    ): Boolean {
        val newEnd = newStart.plusSeconds(newDurationMinutes.toLong() * 60)

        return appointments.any { existing ->
            if (existing.status == AppointmentStatus.CANCELLED) {
                return@any false
            }

            if (ignoreAppointmentId != null && existing.id == ignoreAppointmentId) {
                return@any false
            }

            val existingStart = existing.appointmentDate ?: return@any false
            val existingDuration = existing.durationMinutes.coerceAtLeast(1)
            val existingEnd = existingStart.plusSeconds(existingDuration.toLong() * 60)

            newStart.isBefore(existingEnd) && newEnd.isAfter(existingStart)
        }
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
                clinicEntity?.id
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

            serviceIds =
                specialties.mapNotNull { it.id },

            serviceNames =
                specialties.map { it.name },

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