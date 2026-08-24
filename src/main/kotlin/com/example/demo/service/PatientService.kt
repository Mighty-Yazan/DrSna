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

        val doctors = clinicDoctorRepository.findAllByClinic_Id(clinic.user!!.id!!)
            .mapNotNull { relation ->
                val doctor = relation.doctor ?: return@mapNotNull null
                val schedules = scheduleRepository
                    .findAllByClinicIdAndDoctorIdAndDayOfWeekOrderByStartTimeAsc(clinicId, doctor.id!!, DayOfWeek.MONDAY)
                    .let { monday ->
                        // Fetch the remaining days as well; keeping this explicit avoids relying on an ORM collection.
                        DayOfWeek.values().flatMap { day ->
                            if (day == DayOfWeek.MONDAY) monday
                            else scheduleRepository.findAllByClinicIdAndDoctorIdAndDayOfWeekOrderByStartTimeAsc(clinicId, doctor.id!!, day)
                        }
                    }
                    .sortedWith(compareBy<Schedule> { it.dayOfWeek.value }.thenBy { it.startTime })
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

        val now = Instant.now()
        return doctorRelations.flatMap { relation ->
            val doctor = relation.doctor ?: return@flatMap emptyList()
            val schedules = scheduleRepository.findAllByClinicIdAndDoctorIdAndDayOfWeekOrderByStartTimeAsc(
                clinicId, doctor.id!!, date.dayOfWeek
            )
            val from = date.atStartOfDay(zoneId).toInstant()
            val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val booked = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctor.id!!, from, to)
                .mapNotNull { it.appointmentDate }
                .toHashSet()

            schedules.flatMap { schedule ->
                generateSlots(schedule.startTime, schedule.endTime).mapNotNull { time ->
                    val appointmentAt = date.atTime(time).atZone(zoneId).toOffsetDateTime()
                    val instant = appointmentAt.toInstant()
                    if (instant.isBefore(now)) return@mapNotNull null
                    AvailabilitySlotResponse(
                        scheduleId = schedule.id!!,
                        doctorId = doctor.id!!,
                        doctorName = doctor.fullName,
                        date = date,
                        time = time,
                        appointmentAt = appointmentAt,
                        available = instant !in booked
                    )
                }
            }
        }.sortedWith(compareBy<AvailabilitySlotResponse> { it.time }.thenBy { it.doctorName })
    }

    @Transactional
    fun createAppointment(patientEmail: String, request: CreateAppointmentRequest): AppointmentResponse {
        val clinicId = request.clinicId ?: throw AppException("Clinic ID is required")
        val doctorId = request.doctorId ?: throw AppException("Doctor ID is required")
        val serviceId = request.serviceId ?: throw AppException("Service ID is required")
        val scheduleId = request.scheduleId ?: throw AppException("Schedule ID is required")
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
        val service = servicesRepository.findById(serviceId)
            .orElseThrow { ResourceNotFoundException("Service not found with ID: $serviceId") }
        val schedule = scheduleRepository.findById(scheduleId)
            .orElseThrow { ResourceNotFoundException("Schedule not found with ID: $scheduleId") }

        if (doctor.role != Role.DOCTOR) throw AppException("Selected user is not a doctor")
        if (patient.role != Role.PATIENT) throw AppException("Only patients can create appointments")
        if (service.clinic?.id != clinicId) throw AppException("Service does not belong to the selected clinic")
        if (schedule.clinic?.id != clinicId || schedule.doctor?.id != doctorId) {
            throw AppException("Schedule does not belong to the selected clinic and doctor")
        }
        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinic.user!!.id!!, doctorId)) {
            throw AppException("Doctor is not associated with the selected clinic")
        }
        if (date.dayOfWeek != schedule.dayOfWeek) {
            throw AppException("Selected date does not match the doctor's schedule")
        }
        if (!isValidSlot(schedule.startTime, schedule.endTime, time)) {
            throw AppException("Selected time is outside the doctor's working schedule")
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
        val relations = clinicDoctorRepository.findAllByClinic_Id(clinic.user!!.id!!)
        val from = date.atStartOfDay(zoneId).toInstant()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val now = Instant.now()
        return relations.any { relation ->
            val doctor = relation.doctor ?: return@any false
            val booked = appointmentRepository.findAllByDoctorIdAndAppointmentDateBetween(doctor.id!!, from, to)
                .mapNotNull { it.appointmentDate }.toHashSet()
            scheduleRepository.findAllByClinicIdAndDoctorIdAndDayOfWeekOrderByStartTimeAsc(clinicId, doctor.id!!, date.dayOfWeek)
                .flatMap { schedule -> generateSlots(schedule.startTime, schedule.endTime) }
                .any { time ->
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
        return !time.isBefore(start) && time.plusMinutes(30).compareTo(end) <= 0
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
        scheduleId = id!!,
        dayOfWeek = dayOfWeek,
        startTime = startTime,
        endTime = endTime,
        workingHoursDoctor = workingHoursDoctor
    )

    private fun Appointment.toResponse(zoneId: ZoneId) = AppointmentResponse(
        appointmentId = id!!,
        patientId = patient!!.id!!,
        clinicId = service!!.clinic!!.id!!,
        doctorId = doctor!!.id!!,
        doctorName = doctor!!.fullName,
        serviceId = service!!.id!!,
        serviceName = service!!.serviceName,
        scheduleId = schedule!!.id!!,
        appointmentAt = appointmentDate!!.atZone(zoneId).toOffsetDateTime(),
        createdAt = createdAt.atZone(zoneId).toOffsetDateTime()
    )
}
