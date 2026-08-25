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
    private val servicesRepository: ServicesRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository,
    private val scheduleRepository: ScheduleRepository,
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository
) {
    private val zoneId = ZoneId.of("Asia/Amman")

    @Transactional(readOnly = true)
    fun searchClinics(
        patientEmail: String,
        name: String?,
        service: String?,
        city: City?,
        date: LocalDate?,
        availableOnly: Boolean
    ): List<ClinicSummaryResponse> {
        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow { ResourceNotFoundException("Authenticated patient was not found") }

        val normalizedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedService = service?.trim()?.takeIf { it.isNotEmpty() }
        if (availableOnly && date == null) {
            throw AppException("A date is required when filtering by availability")
        }
        val clinics = clinicRepository.search(normalizedName, city, patient.city)

        return clinics.asSequence()
            .filter { clinic ->
                normalizedService == null || servicesRepository.existsByClinicIdAndServiceNameIgnoreCase(clinic.id!!, normalizedService)
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

        val services = servicesRepository.findAllByClinicId(clinicId).map {
            ServiceDetailsResponse(it.id!!, it.serviceName, it.descriptionOfService)
        }

        val allClinicSchedules = scheduleRepository.findByClinicId(clinicId)

        val doctors = clinicDoctorRepository.findAllByClinic_Id(clinic.user!!.id!!)
            .mapNotNull { relation ->
                val doctor = relation.doctor ?: return@mapNotNull null

                val schedules = allClinicSchedules
                    .filter { it.doctor?.id == doctor.id }
                    .sortedWith(compareBy<Schedule> { it.dayOfWeek?.value ?: 0 }.thenBy { it.startTime })
                    .map { it.toResponse() }

                DoctorDetailsResponse(doctor.id!!, doctor.fullName, doctor.email, schedules)
            }

        return ClinicDetailsResponse(
            clinicId = clinic.id!!,
            clinicUserId = clinic.user!!.id!!,
            clinicName = clinic.clinicName,
            city = clinic.user!!.city,
            rating = clinic.rating,
            detailedAddress = clinic.detailedAddress,
            phoneNumber = clinic.phoneNumber,
            socialLinks = clinic.socialLinks,
            checkingFee = clinic.checkingFee,
            description = clinic.description,
            workingHours = clinic.workingHours,
            services = services,
            doctors = doctors
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

        val isClinicHoliday = scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinicId, date, ScheduleType.HOLIDAY)
        if (isClinicHoliday) return emptyList()

        val clinicSchedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(clinicId, ScheduleType.CLINIC_HOURS, date.dayOfWeek)
            ?: return emptyList()

        val allSchedules = scheduleRepository.findByClinicId(clinicId)
        val now = Instant.now()

        return doctorRelations.flatMap { relation ->
            val doctor = relation.doctor ?: return@flatMap emptyList()

            val isDoctorHoliday = allSchedules.any {
                it.doctor?.id == doctor.id && it.type == ScheduleType.HOLIDAY && it.specificDate == date
            }
            if (isDoctorHoliday) return@flatMap emptyList()

            val doctorSchedule = allSchedules.firstOrNull {
                it.doctor?.id == doctor.id && it.type == ScheduleType.DOCTOR_SHIFT && it.dayOfWeek == date.dayOfWeek
            } ?: return@flatMap emptyList()

            val startShift = maxOf(clinicSchedule.startTime!!, doctorSchedule.startTime!!)
            val endShift = minOf(clinicSchedule.endTime!!, doctorSchedule.endTime!!)
            if (startShift >= endShift) return@flatMap emptyList()

            val from = date.atStartOfDay(zoneId).toInstant()
            val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val booked = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctor.id!!, from, to)
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
        val serviceIdReq = request.serviceId ?: throw AppException("Service ID is required")
        val scheduleIdReq = request.scheduleId ?: throw AppException("Schedule ID is required")
        val date = request.appointmentDate ?: throw AppException("Appointment date is required")
        val time = request.appointmentTime ?: throw AppException("Appointment time is required")

        requireDateNotPast(date)
        if (time.minute % 30 != 0 || time.second != 0 || time.nano != 0) {
            throw AppException("Appointments must start on a 30-minute slot")
        }

        val patient = userRepository.findByEmail(patientEmail)
            .orElseThrow { ResourceNotFoundException("Patient not found") }
        val clinic = clinicRepository.findById(clinicId)
            .orElseThrow { ResourceNotFoundException("Clinic not found with ID: $clinicId") }
        val doctor = userRepository.findById(doctorId)
            .orElseThrow { ResourceNotFoundException("Doctor not found with ID: $doctorId") }

        val service = try {
            val uuid = UUID.fromString(serviceIdReq.toString())
            servicesRepository.findById(uuid).orElseThrow { ResourceNotFoundException("Service not found") }
        } catch (_: IllegalArgumentException) {
            val parsedLong = serviceIdReq.toString().toLongOrNull()
            if (parsedLong != null) {
                servicesRepository.findAll().firstOrNull { it.id.toString() == serviceIdReq.toString() }
                    ?: throw ResourceNotFoundException("Service not found")
            } else {
                throw ResourceNotFoundException("Service not found")
            }
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

        val serviceClinicId = service.clinic?.id
        if (serviceClinicId != null && serviceClinicId != clinicId) {
            throw AppException("Service does not belong to the selected clinic")
        }

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

        val clinicSchedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(clinicId, ScheduleType.CLINIC_HOURS, date.dayOfWeek)
            ?: throw AppException("Clinic is closed on this day")

        val doctorSchedule = scheduleRepository.findByClinicId(clinicId).firstOrNull {
            it.doctor?.id == doctorId && it.type == ScheduleType.DOCTOR_SHIFT && it.dayOfWeek == date.dayOfWeek
        } ?: throw AppException("Doctor is not scheduled to work on this day")

        val startShift = maxOf(clinicSchedule.startTime!!, doctorSchedule.startTime!!)
        val endShift = minOf(clinicSchedule.endTime!!, doctorSchedule.endTime!!)

        if (!isValidSlot(startShift, endShift, time)) {
            throw AppException("Selected time is outside the doctor's available working hours")
        }

        val appointmentAt = date.atTime(time).atZone(zoneId).toInstant()
        if (appointmentAt.isBefore(Instant.now())) {
            throw AppException("Appointment time must be in the future")
        }
        if (appointmentRepository.existsByDoctorIdAndAppointmentDate(doctorId, appointmentAt)) {
            throw DuplicateResourceException("The selected appointment slot is already booked")
        }

        val appointment = Appointment(
            clinic = clinic.user,
            doctor = doctor,
            patient = patient,
            service = service,
            schedule = schedule,
            appointmentDate = appointmentAt
        )

        val saved = try {
            appointmentRepository.saveAndFlush(appointment)
        } catch (_: DataIntegrityViolationException) {
            throw DuplicateResourceException("The selected appointment slot is already booked")
        }

        return saved.toResponse(zoneId)
    }

    private fun hasAnyAvailableSlot(clinicId: UUID, date: LocalDate): Boolean {
        if (date.isBefore(LocalDate.now(zoneId))) return false
        val clinic = clinicRepository.findById(clinicId).orElse(null) ?: return false
        val clinicUser = clinic.user ?: return false

        val isClinicHoliday = scheduleRepository.existsByClinicIdAndSpecificDateAndType(clinicId, date, ScheduleType.HOLIDAY)
        if (isClinicHoliday) return false

        val clinicSchedule = scheduleRepository.findByClinicIdAndTypeAndDayOfWeek(clinicId, ScheduleType.CLINIC_HOURS, date.dayOfWeek)
            ?: return false

        val relations = clinicDoctorRepository.findAllByClinic_Id(clinicUser.id!!)
        val from = date.atStartOfDay(zoneId).toInstant()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val now = Instant.now()
        val allSchedules = scheduleRepository.findByClinicId(clinicId)

        return relations.any { relation ->
            val doctor = relation.doctor ?: return@any false

            val isDoctorHoliday = allSchedules.any {
                it.doctor?.id == doctor.id && it.type == ScheduleType.HOLIDAY && it.specificDate == date
            }
            if (isDoctorHoliday) return@any false

            val doctorSchedule = allSchedules.firstOrNull {
                it.doctor?.id == doctor.id && it.type == ScheduleType.DOCTOR_SHIFT && it.dayOfWeek == date.dayOfWeek
            } ?: return@any false

            val start = maxOf(clinicSchedule.startTime!!, doctorSchedule.startTime!!)
            val end = minOf(clinicSchedule.endTime!!, doctorSchedule.endTime!!)
            if (start >= end) return@any false

            val booked = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctor.id!!, from, to)
                .mapNotNull { it.appointmentDate }.toHashSet()

            generateSlots(start, end).any { time ->
                val instant = date.atTime(time).atZone(zoneId).toInstant()
                instant.isAfter(now) && instant !in booked
            }
        }
    }

    private fun generateSlots(start: LocalTime, end: LocalTime): List<LocalTime> {
        if (!start.isBefore(end)) return emptyList()
        val slots = mutableListOf<LocalTime>()
        var current = start
        while (!current.plusMinutes(30).isAfter(end)) {
            slots += current
            current = current.plusMinutes(30)
        }
        return slots
    }

    private fun isValidSlot(start: LocalTime, end: LocalTime, time: LocalTime): Boolean {
        return !time.isBefore(start) && time.plusMinutes(30) <= end
    }

    private fun requireDateNotPast(date: LocalDate) {
        if (date.isBefore(LocalDate.now(zoneId))) {
            throw AppException("Appointment date cannot be in the past")
        }
    }

    private fun Clinic.toSummary(available: Boolean?) = ClinicSummaryResponse(
        clinicId = id!!,
        clinicUserId = user!!.id!!,
        clinicName = clinicName,
        city = user!!.city,
        rating = rating,
        detailedAddress = detailedAddress,
        phoneNumber = phoneNumber,
        checkingFee = checkingFee,
        workingHours = workingHours,
        services = servicesRepository.findAllByClinicId(id!!).map { it.serviceName },
        available = available
    )

    private fun Schedule.toResponse() = ScheduleResponse(
        scheduleId = id,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        workingHoursDoctor = if (startTime != null && endTime != null) "$startTime - $endTime" else null
    )

    private fun Appointment.toResponse(zoneId: ZoneId) = AppointmentResponse(
        appointmentId = id!!,
        patientId = patient!!.id!!,
        clinicId = service?.clinic?.id ?: clinic!!.id!!,
        doctorId = doctor!!.id!!,
        doctorName = doctor!!.fullName,
        serviceId = service!!.id!!,
        serviceName = service!!.serviceName,
        scheduleId = schedule?.id,
        appointmentAt = appointmentDate!!.atZone(zoneId).toOffsetDateTime(),
        createdAt = createdAt.atZone(zoneId).toOffsetDateTime()
    )
}