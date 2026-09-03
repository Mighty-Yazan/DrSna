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
class PatientService(
    private val clinicRepository: ClinicRepository,
    private val specialtyRepository: SpecialtyRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository,
    private val scheduleRepository: ScheduleRepository,
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository,
    private val clinicSpecialtyRepository: ClinicSpecialtyRepository,
    private val reviewRepository: ReviewRepository,
    private val favoriteDoctorRepository: FavoriteDoctorRepository
) {
    private val zoneId = ZoneId.of("UTC")

    @Transactional(readOnly = true)
    fun searchClinics(
        patientEmail: String,
        name: String?,
        service: String?,
        city: City?,
        specialty: String?,
        date: LocalDate?,
        availableOnly: Boolean
    ): List<ClinicSummaryResponse> {
        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow { ResourceNotFoundException("Authenticated patient was not found") }

        val normalizedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedService = service?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedSpecialty = specialty?.trim()?.takeIf { it.isNotEmpty() }
        if (availableOnly && date == null) {
            throw AppException("A date is required when filtering by availability")
        }
        val clinics = clinicRepository.search(normalizedName, city, patient.city)

        return clinics.asSequence()
            .filter { clinic ->
                normalizedService == null || clinicSpecialtyRepository.findAllByClinicId(clinic.id!!).any { it.specialty?.name?.equals(normalizedService, ignoreCase = true) == true }
            }
            .filter { clinic ->
                normalizedSpecialty == null || clinicSpecialtyRepository.findAllByClinicId(clinic.id!!).any { it.specialty?.name?.equals(normalizedSpecialty, ignoreCase = true) == true }
            }
            .map { clinic ->
                val available = date?.let { hasAnyAvailableSlot(clinic.id!!, it) }
                clinic.toSummary(available)
            }
            .filter { !availableOnly || it.available == true }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getClinicDetails(clinicId: UUID): ClinicDetailsResponse {
        val clinic = clinicRepository.findDetailsById(clinicId)
            .orElseThrow { ResourceNotFoundException("Clinic not found with ID: $clinicId") }

        val services = clinicSpecialtyRepository.findAllByClinicId(clinicId).mapNotNull { it.specialty }.map {
            ServiceDetailsResponse(it.id!!, it.name, "")
        }

        val allClinicSchedules = scheduleRepository.findByClinicId(clinicId)

        val specialties = clinicSpecialtyRepository.findAllByClinicId(clinicId).mapNotNull { it.specialty?.name }
        val reviews = reviewRepository.findAllByClinicIdOrderByCreatedAtDesc(clinicId).map { it.toReviewResponse(zoneId) }

        val doctors = clinicDoctorRepository.findAllByClinic_Id(clinic.user!!.id!!)
            .filter { it.doctor?.isActive == true }
            .filter { relation ->
                allClinicSchedules.any { 
                    it.doctor?.id == relation.doctor?.id && 
                    it.type == ScheduleType.DOCTOR_SHIFT &&
                    it.specificDate != null && 
                    !it.specificDate!!.isBefore(LocalDate.now(zoneId))
                }
            }
            .mapNotNull { relation ->
                val doctor = relation.doctor ?: return@mapNotNull null

                val schedules = allClinicSchedules
                    .filter { it.doctor?.id == doctor.id && it.type == ScheduleType.DOCTOR_SHIFT }
                    .sortedWith(compareBy<Schedule> { it.specificDate }.thenBy { it.startTime })
                    .map { it.toResponse() }

                DoctorDetailsResponse(doctor.id!!, doctor.fullName, doctor.email, doctor.specialty, doctor.bio, doctor.isActive, schedules)
            }

        val clinicHoursList = allClinicSchedules
            .filter { it.type == ScheduleType.CLINIC_HOURS && it.dayOfWeek != null }
            .sortedBy { it.dayOfWeek?.value ?: 0 }
            .map { it.toResponse() }

        return ClinicDetailsResponse(
            clinicId = clinic.id!!,
            clinicUserId = clinic.user!!.id!!,
            clinicName = clinic.clinicName,
            city = clinic.user!!.city,
            rating = clinic.rating,
            detailedAddress = clinic.detailedAddress,
            phoneNumber = clinic.phoneNumber,
            email = clinic.user!!.email,
            socialLinks = clinic.socialLinks,
            checkingFee = clinic.checkingFee,
            description = clinic.description,
            workingHours = clinic.workingHours,
            clinicHours = clinicHoursList,
            services = services,
            specialties = specialties,
            doctors = doctors,
            reviews = reviews
        )
    }

    @Transactional(readOnly = true)
    fun getAvailability(clinicId: UUID, date: LocalDate, doctorId: UUID?): List<AvailabilitySlotResponse> {
        requireDateNotPast(date)
        val clinic = clinicRepository.findById(clinicId)
            .orElseThrow { ResourceNotFoundException("Clinic not found with ID: $clinicId") }
        val clinicUserId = clinic.user!!.id!!

        val relations = clinicDoctorRepository.findAllByClinic_Id(clinicUserId)
        val doctorRelations = if (doctorId == null) relations else relations.filter { it.doctor?.id == doctorId }
        if (doctorId != null && doctorRelations.isEmpty()) {
            throw ResourceNotFoundException("Doctor is not associated with this clinic")
        }

        val allSchedules = scheduleRepository.findByClinicId(clinicId)

        // ── 1. Check if Clinic is Open on this Day of the Week ──
        val dayOfWeek = date.dayOfWeek
        val clinicHours = allSchedules.firstOrNull {
            it.type == ScheduleType.CLINIC_HOURS && it.dayOfWeek == dayOfWeek
        }

        // If clinic is closed on this day (no record, or null times), return NO slots
        if (clinicHours == null || clinicHours.startTime == null || clinicHours.endTime == null) {
            return emptyList()
        }

        // ── 2. Check Clinic Specific Holiday ──
        val isClinicHoliday = scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinicId, date, ScheduleType.HOLIDAY)
        if (isClinicHoliday) return emptyList()

        val now = Instant.now()

        return doctorRelations.flatMap { relation ->
            val doctor = relation.doctor ?: return@flatMap emptyList()
            if (!doctor.isActive) return@flatMap emptyList()

            val isDoctorHoliday = allSchedules.any {
                it.doctor?.id == doctor.id && it.type == ScheduleType.HOLIDAY && it.specificDate == date
            }
            if (isDoctorHoliday) return@flatMap emptyList()

            val doctorSchedule = allSchedules.firstOrNull {
                it.doctor?.id == doctor.id && it.type == ScheduleType.DOCTOR_SHIFT && it.specificDate == date
            } ?: return@flatMap emptyList()

            // Intersect doctor shift with clinic working hours
            val startShift = maxOf(clinicHours.startTime!!, doctorSchedule.startTime!!)
            val endShift = minOf(clinicHours.endTime!!, doctorSchedule.endTime!!)
            if (!startShift.isBefore(endShift)) return@flatMap emptyList()

            val from = date.atStartOfDay(zoneId).toInstant()
            val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val booked = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctor.id!!, from, to)
                .filter { it.status != AppointmentStatus.CANCELLED }
                .mapNotNull { it.appointmentDate }
                .toHashSet()

            generateSlots(startShift, endShift).mapNotNull { time ->
                val appointmentAt = date.atTime(time).atZone(zoneId).toOffsetDateTime()
                val instant = appointmentAt.toInstant()
                if (instant.isBefore(now)) return@mapNotNull null
                AvailabilitySlotResponse(
                    scheduleId = doctorSchedule.id,
                    doctorId = doctor.id!!,
                    doctorName = doctor.fullName,
                    date = date,
                    time = time,
                    appointmentAt = appointmentAt,
                    available = instant !in booked
                )
            }
        }.sortedWith(compareBy<AvailabilitySlotResponse> { it.time }.thenBy { it.doctorName })
    }

    @Transactional
    fun createAppointment(patientEmail: String, request: CreateAppointmentRequest): AppointmentResponse {
        val clinicId = request.clinicId ?: throw AppException("Clinic ID is required")
        val doctorId = request.doctorId ?: throw AppException("Doctor ID is required")
        val serviceIdsReq = request.serviceIds
        if (serviceIdsReq.isEmpty()) {
            throw AppException("At least one Service ID is required")
        }
        val scheduleIdReq = request.scheduleId ?: throw AppException("Schedule ID is required")
        val date = request.appointmentDate ?: throw AppException("Appointment date is required")
        val time = request.appointmentTime ?: throw AppException("Appointment time is required")

        requireDateNotPast(date)

        if (time.minute != 0 || time.second != 0 || time.nano != 0) {
            throw AppException("Appointments must start on a 60-minute slot (top of the hour)")
        }

        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow { ResourceNotFoundException("Patient not found") }
        val clinic = clinicRepository.findById(clinicId)
            .orElseThrow { ResourceNotFoundException("Clinic not found with ID: $clinicId") }
        val doctor = userRepository.findById(doctorId)
            .orElseThrow { ResourceNotFoundException("Doctor not found with ID: $doctorId") }

        val specialtiesList = specialtyRepository.findAllById(serviceIdsReq)
        if (specialtiesList.isEmpty()) {
            throw ResourceNotFoundException("No valid specialties found for the provided IDs")
        }

        val parsedScheduleId = scheduleIdReq.toString().toLongOrNull()
        val schedule = if (parsedScheduleId != null) {
            scheduleRepository.findById(parsedScheduleId).orElseThrow { ResourceNotFoundException("Schedule not found") }
        } else {
            scheduleRepository.findAll().firstOrNull { it.id.toString() == scheduleIdReq.toString() }
                ?: throw ResourceNotFoundException("Schedule not found")
        }

        if (doctor.role != Role.DOCTOR) throw AppException("Selected user is not a doctor")
        if (patient.role != Role.PATIENT) throw AppException("Only patients can create appointments")
        if (!doctor.isActive) throw AppException("Doctor account is inactive and cannot receive bookings")

        // Removed serviceClinicId check because Specialty does not have a clinic field directly.
        // ClinicSpecialty manages the many-to-many relationship instead.

        if (schedule.clinic?.id != clinicId || schedule.doctor?.id != doctorId) {
            throw AppException("Schedule does not belong to the selected clinic and doctor")
        }
        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinic.user!!.id!!, doctorId)) {
            throw AppException("Doctor is not associated with the selected clinic")
        }

        val isClinicHoliday = scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinicId, date, ScheduleType.HOLIDAY)
        val isDoctorHoliday = scheduleRepository.findByClinicId(clinicId).any {
            it.doctor?.id == doctorId && it.type == ScheduleType.HOLIDAY && it.specificDate == date
        }
        if (isClinicHoliday || isDoctorHoliday) {
            throw AppException("The clinic or doctor is not available on this date due to a holiday/closure")
        }

        val doctorSchedule = scheduleRepository.findByClinicId(clinicId).firstOrNull {
            it.doctor?.id == doctorId && it.type == ScheduleType.DOCTOR_SHIFT && it.specificDate == date
        } ?: throw AppException("Doctor is not scheduled on this day")

        val startShift = doctorSchedule.startTime!!
        val endShift = doctorSchedule.endTime!!

        if (!isValidSlot(startShift, endShift, time)) {
            throw AppException("Selected time is outside the doctor's available working hours")
        }

        val appointmentAt =
            date
                .atTime(time)
                .atZone(zoneId)
                .toInstant()
        if (appointmentAt.isBefore(Instant.now())) {
            throw AppException(
                "Appointment time must be in the future"
            )
        }
        val bookingKey =
            "${doctorId}:$appointmentAt"
        if (
            appointmentRepository.existsByBookingKey(
                bookingKey
            )
        ) {
            throw DuplicateResourceException(
                "The selected appointment slot is already booked"
            )
        }
        val appointment = Appointment(
            clinic = clinic.user,
            doctor = doctor,
            patient = patient,
            specialties = specialtiesList.toMutableList(),
            schedule = schedule,
            appointmentDate = appointmentAt,
            status = AppointmentStatus.PENDING,
            bookingKey = bookingKey
        )
        val saved =
            try {
                appointmentRepository.saveAndFlush(
                    appointment
                )
            } catch (
                _: DataIntegrityViolationException
            ) {
                throw DuplicateResourceException(
                    "The selected appointment slot is already booked"
                )
            }
        return saved.toResponse(zoneId)
    }

    @Transactional
    fun createReview(patientEmail: String, request: CreateReviewRequest): ReviewResponse {
        val appointmentId = request.appointmentId ?: throw AppException("Appointment ID is required")
        val patient = userRepository.findByEmail(patientEmail).orElseThrow { ResourceNotFoundException("Patient not found") }
        val appointment = appointmentRepository.findById(appointmentId).orElseThrow { ResourceNotFoundException("Appointment not found") }

        if (appointment.patient?.id != patient.id) {
            throw AppException(
                "You can review only your own appointment"
            )
        }
        if (
            appointment.status !=
            AppointmentStatus.COMPLETED
        ) {
            throw AppException(
                "Only completed appointments can be reviewed"
            )
        }
        if (
            reviewRepository.existsByAppointmentId(
                appointmentId
            )
        ) {
            throw DuplicateResourceException(
                "This appointment has already been reviewed"
            )
        }

        val review = reviewRepository.save(Review(appointment = appointment, patient = patient, clinic = clinicRepository.findByUserId(appointment.clinic!!.id!!).orElseThrow { ResourceNotFoundException("Clinic not found") }, doctor = appointment.doctor, rating = request.rating, comment = request.comment?.trim()))
        recalculateClinicRating(review.clinic!!.id!!)
        return review.toReviewResponse(zoneId)
    }

    private fun recalculateClinicRating(clinicId: UUID) {
        val clinic = clinicRepository.findById(clinicId).orElseThrow { ResourceNotFoundException("Clinic not found") }
        val reviews = reviewRepository.findAllByClinicId(clinicId)
        clinic.rating = if (reviews.isEmpty()) java.math.BigDecimal.ZERO else reviews.map { it.rating }.average().toBigDecimal().setScale(1, java.math.RoundingMode.HALF_UP)
        clinicRepository.save(clinic)
    }
    private fun hasAnyAvailableSlot(clinicId: UUID, date: LocalDate): Boolean {
        if (date.isBefore(LocalDate.now(zoneId))) return false
        val clinic = clinicRepository.findById(clinicId).orElse(null) ?: return false
        val clinicUser = clinic.user ?: return false

        val allSchedules = scheduleRepository.findByClinicId(clinicId)

        // Verify clinic is open on this day of the week
        val dayOfWeek = date.dayOfWeek
        val clinicHours = allSchedules.firstOrNull {
            it.type == ScheduleType.CLINIC_HOURS && it.dayOfWeek == dayOfWeek
        }
        if (clinicHours == null || clinicHours.startTime == null || clinicHours.endTime == null) {
            return false
        }

        val isClinicHoliday = scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinicId, date, ScheduleType.HOLIDAY)
        if (isClinicHoliday) return false

        val relations = clinicDoctorRepository.findAllByClinic_Id(clinicUser.id!!)
        val from = date.atStartOfDay(zoneId).toInstant()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val now = Instant.now()

        return relations.any { relation ->
            val doctor = relation.doctor ?: return@any false
            if (!doctor.isActive) return@any false

            val isDoctorHoliday = allSchedules.any {
                it.doctor?.id == doctor.id && it.type == ScheduleType.HOLIDAY && it.specificDate == date
            }
            if (isDoctorHoliday) return@any false

            val doctorSchedule = allSchedules.firstOrNull {
                it.doctor?.id == doctor.id && it.type == ScheduleType.DOCTOR_SHIFT && it.specificDate == date
            } ?: return@any false

            val start = maxOf(clinicHours.startTime!!, doctorSchedule.startTime!!)
            val end = minOf(clinicHours.endTime!!, doctorSchedule.endTime!!)
            if (!start.isBefore(end)) return@any false

            val booked = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctor.id!!, from, to)
                .filter { it.status != AppointmentStatus.CANCELLED }
                .mapNotNull { it.appointmentDate }.toHashSet()

            generateSlots(start, end).any { time ->
                val instant = date.atTime(time).atZone(zoneId).toInstant()
                instant.isAfter(now) && instant !in booked
            }
        }
    }

    private fun generateSlots(
        start: LocalTime,
        end: LocalTime
    ): List<LocalTime> {

        if (!start.isBefore(end)) {
            return emptyList()
        }

        /*
         * Every appointment is exactly 60 minutes.
         *
         * If the schedule starts at:
         * 09:30
         *
         * We do NOT generate:
         * 09:30
         *
         * because the booking API requires appointments to start
         * on the hour.
         *
         * The first valid slot becomes:
         * 10:00
         */

        var current =
            if (
                start.minute == 0 &&
                start.second == 0 &&
                start.nano == 0
            ) {
                start
            } else {
                start
                    .plusMinutes(
                        (60 - start.minute).toLong()
                    )
                    .withSecond(0)
                    .withNano(0)
            }

        val slots =
            mutableListOf<LocalTime>()

        while (
            !current
                .plusMinutes(60)
                .isAfter(end)
        ) {

            slots += current

            current =
                current.plusMinutes(60)
        }

        return slots
    }

    private fun buildBookingKey(
        doctorId: UUID,
        appointmentAt: Instant
    ): String =
        "$doctorId:$appointmentAt"

    private fun isValidSlot(start: LocalTime, end: LocalTime, time: LocalTime): Boolean {
        return !time.isBefore(start) && !time.plusMinutes(60).isAfter(end)
    }

    private fun requireDateNotPast(date: LocalDate) {
        if (date.isBefore(LocalDate.now(zoneId))) {
            throw AppException("Appointment date cannot be in the past")
        }
    }

    private fun getNextAvailableSlot(clinicId: UUID): String? {
        val today = LocalDate.now(zoneId)
        for (i in 0..14L) {
            val date = today.plusDays(i)
            // Filter only slots that are actually available (available == true)
            val availableSlots = getAvailability(clinicId, date, null).filter { it.available }

            if (availableSlots.isNotEmpty()) {
                val earliest = availableSlots.minByOrNull { it.time } ?: continue
                val dateStr = when (i) {
                    0L -> "Today"
                    1L -> "Tomorrow"
                    else -> date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                }
                return "$dateStr, ${earliest.time}"
            }
        }
        return null
    }

    private fun Clinic.toSummary(available: Boolean?): ClinicSummaryResponse {
        val today = LocalDate.now(zoneId).dayOfWeek
        val allSchedules = scheduleRepository.findAllByClinicId(id!!)
        val schedule = allSchedules.firstOrNull { it.type == ScheduleType.CLINIC_HOURS && it.dayOfWeek == today }
        
        val dynamicWorkingHours = if (schedule?.startTime != null && schedule.endTime != null) {
            "${schedule.startTime} - ${schedule.endTime}"
        } else {
            val hasClinicHours = allSchedules.any { it.type == ScheduleType.CLINIC_HOURS }
            if (hasClinicHours) "Closed Today" else workingHours
        }

        return ClinicSummaryResponse(
            clinicId = id!!,
            clinicUserId = user!!.id!!,
            clinicName = clinicName,
            city = user!!.city,
            rating = rating,
            detailedAddress = detailedAddress,
            phoneNumber = phoneNumber,
            checkingFee = checkingFee,
            description = description,
            workingHours = dynamicWorkingHours,
            services = clinicSpecialtyRepository.findAllByClinicId(id!!).mapNotNull { it.specialty?.name },
            specialties = clinicSpecialtyRepository.findAllByClinicId(id!!).mapNotNull { it.specialty?.name },
            doctors = clinicDoctorRepository.findAllByClinic_Id(user!!.id!!)
                .filter { it.doctor?.isActive == true }
                .filter { relation -> 
                    allSchedules.any { 
                        it.doctor?.id == relation.doctor?.id && 
                        it.type == ScheduleType.DOCTOR_SHIFT && 
                        it.specificDate != null && 
                        !it.specificDate!!.isBefore(LocalDate.now(zoneId))
                    }
                }
                .map { it.doctor!!.fullName },
            available = available,
            nextAvailableSlot = getNextAvailableSlot(id!!)
        )
    }

    private fun Schedule.toResponse() = ScheduleResponse(
        scheduleId = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        workingHoursDoctor = if (startTime != null && endTime != null) "$startTime - $endTime" else null
    )

    private fun Review.toReviewResponse(zoneId: ZoneId) = ReviewResponse(
        reviewId = id!!, appointmentId = appointment!!.id!!, patientId = patient!!.id!!, patientName = patient!!.fullName,
        doctorId = doctor!!.id!!, doctorName = doctor!!.fullName, rating = rating, comment = comment, reply = reply,
        replyAt = replyAt?.atZone(zoneId)?.toOffsetDateTime(), createdAt = createdAt.atZone(zoneId).toOffsetDateTime()
    )

    private fun Appointment.toResponse(zoneId: ZoneId) = AppointmentResponse(
        appointmentId = id!!,
        patientId = patient!!.id!!,
        clinicId = clinic!!.id!!,
        doctorId = doctor!!.id!!,
        doctorName = doctor!!.fullName,
        serviceIds = specialties.mapNotNull { it.id },
        serviceNames = specialties.map { it.name },
        scheduleId = schedule?.id,
        appointmentAt = appointmentDate!!.atZone(zoneId).toOffsetDateTime(),
        createdAt = createdAt.atZone(zoneId).toOffsetDateTime(),
        status = status
    )

    @Transactional
    fun addFavoriteDoctor(patientEmail: String, doctorId: UUID) {
        val patient = userRepository.findByEmail(patientEmail).orElseThrow { ResourceNotFoundException("Patient not found") }
        val doctor = userRepository.findById(doctorId).orElseThrow { ResourceNotFoundException("Doctor not found") }
        
        if (doctor.role != Role.DOCTOR) {
            throw AppException("Only doctors can be added to favorites")
        }
        
        if (favoriteDoctorRepository.existsByPatientIdAndDoctorId(patient.id!!, doctor.id!!)) {
            throw DuplicateResourceException("Doctor is already in favorites")
        }
        
        favoriteDoctorRepository.save(FavoriteDoctor(patient = patient, doctor = doctor))
    }

    @Transactional
    fun removeFavoriteDoctor(patientEmail: String, doctorId: UUID) {
        val patient = userRepository.findByEmail(patientEmail).orElseThrow { ResourceNotFoundException("Patient not found") }
        
        if (!favoriteDoctorRepository.existsByPatientIdAndDoctorId(patient.id!!, doctorId)) {
            throw ResourceNotFoundException("Doctor is not in your favorites")
        }
        
        favoriteDoctorRepository.deleteByPatientIdAndDoctorId(patient.id!!, doctorId)
    }

    @Transactional(readOnly = true)
    fun getFavoriteDoctors(patientEmail: String): List<FavoriteDoctorResponse> {
        val patient = userRepository.findByEmail(patientEmail).orElseThrow { ResourceNotFoundException("Patient not found") }
        val favorites = favoriteDoctorRepository.findByPatientId(patient.id!!)
        
        return favorites.map { fav ->
            val doctor = fav.doctor
            val clinicRelation = clinicDoctorRepository.findAllByDoctor_Id(doctor.id!!).firstOrNull()
            val clinicUser = clinicRelation?.clinic
            val clinic = clinicUser?.let { clinicRepository.findByUserId(it.id!!).orElse(null) }
            
            val specialties = if (clinic != null) {
                clinicSpecialtyRepository.findAllByClinicId(clinic.id!!).mapNotNull { it.specialty?.name }.joinToString(", ")
            } else null
            
            FavoriteDoctorResponse(
                doctorId = doctor.id!!,
                doctorName = doctor.fullName,
                clinicName = clinic?.clinicName ?: clinicUser?.fullName,
                specialties = specialties,
                city = doctor.city
            )
        }
    }
}