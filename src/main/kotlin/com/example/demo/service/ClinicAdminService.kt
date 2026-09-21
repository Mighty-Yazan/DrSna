package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.exception.AppException
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.*
import com.example.demo.repository.*
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional
class ClinicAdminService(
    private val clinicRepository: ClinicRepository,
    private val userRepository: UserRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository,
    private val passwordEncoder: PasswordEncoder,
    private val specialtyRepository: SpecialtyRepository,
    private val clinicSpecialtyRepository: ClinicSpecialtyRepository,
    private val reviewRepository: ReviewRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
    private val scheduleRepository: ScheduleRepository,
    private val appointmentRepository: AppointmentRepository
) {

    private val zoneIdUtc = ZoneId.of("UTC")

    // =========================================================================
    // 1. CLINIC PROFILE
    // =========================================================================

    @Transactional(readOnly = true)
    fun getProfile(userEmail: String): ClinicProfileResponse {
        return getClinic(userEmail).toResponse()
    }

    fun updateProfile(userEmail: String, request: ClinicProfileRequest): ClinicProfileResponse {
        val clinic = getClinic(userEmail)

        clinic.clinicName = request.clinicName.trim()
        clinic.phoneNumber = request.phoneNumber?.trim()
        clinic.detailedAddress = request.detailedAddress?.trim()
        clinic.socialLinks = request.socialLinks
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toMutableList()
            ?: mutableListOf()
        clinic.workingHours = request.workingHours?.trim()
        clinic.checkingFee = request.checkingFee
        clinic.description = request.description?.trim()

        clinic.user?.let { user ->
            user.fullName = request.clinicName.trim()
            if (request.city != null) {
                user.city = request.city
            }
            userRepository.save(user)
        }

        if (clinic.applicationStatus == ClinicApplicationStatus.REJECTED) {
            clinic.applicationStatus = ClinicApplicationStatus.PENDING
            clinic.rejectionReason = null
        }

        return clinicRepository.save(clinic).toResponse()
    }

    fun resubmitApplication(userEmail: String, request: ResubmitApplicationRequest): ClinicProfileResponse {
        val clinic = getClinic(userEmail)
        val user = clinic.user ?: throw IllegalStateException("Clinic is missing linked user")

        if (clinic.applicationStatus != ClinicApplicationStatus.REJECTED) {
            throw AppException("Only rejected applications can be resubmitted")
        }

        val newEmail = request.email.trim().lowercase()
        if (newEmail != user.email.lowercase()) {
            if (userRepository.existsByEmail(newEmail)) {
                throw DuplicateResourceException("User with email '${request.email}' already exists")
            }
            user.email = newEmail
        }

        if (!request.password.isNullOrBlank()) {
            if (request.password != request.confirmPassword) {
                throw AppException("Passwords do not match")
            }
            user.password = passwordEncoder.encode(request.password)
                ?: throw AppException("Unable to encode password")
        }

        user.fullName = request.clinicName.trim()
        user.city = request.city
        clinic.clinicName = request.clinicName.trim()

        val newLicenseNumber = request.clinicLicenseNumber.trim()
        if (newLicenseNumber != user.clinicLicenseNumber) {
            if (userRepository.existsByClinicLicenseNumber(newLicenseNumber)) {
                throw DuplicateResourceException("Clinic license number is already registered")
            }
            user.clinicLicenseNumber = newLicenseNumber
        }

        clinic.applicationStatus = ClinicApplicationStatus.PENDING
        clinic.rejectionReason = null

        userRepository.save(user)
        return clinicRepository.save(clinic).toResponse()
    }

    // =========================================================================
    // 2. SPECIALTIES & SERVICES
    // =========================================================================

    @Transactional(readOnly = true)
    fun getAllSpecialties(): List<SpecialtyResponse> {// all the specialties likeTeeth Whitening,Root Canal,the specialties themselves
        return specialtyRepository.findAll().mapNotNull { specialty ->
            val id = specialty.id ?: return@mapNotNull null
            SpecialtyResponse(id, specialty.name, 60)
        }
    }

    @Transactional(readOnly = true)
    fun getSpecialties(userEmail: String): List<SpecialtyResponse> {//the sepcialties choosen by the clinic
        val clinic = getClinic(userEmail)
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        return clinicSpecialtyRepository.findAllByClinicId(clinicId).mapNotNull { relation ->
            val specialty = relation.specialty ?: return@mapNotNull null
            val specialtyId = specialty.id ?: return@mapNotNull null
            SpecialtyResponse(specialtyId, specialty.name, relation.durationMinutes)
        }
    }

    fun addSpecialty(userEmail: String, request: SpecialtyRequest): SpecialtyResponse {
        val clinic = getClinic(userEmail)
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        val name = request.name.trim()
        val specialty = specialtyRepository.findByNameIgnoreCase(name)
            ?: specialtyRepository.save(Specialty(name = name))

        val specialtyId = specialty.id ?: throw IllegalStateException("Specialty missing ID")

        if (!clinicSpecialtyRepository.existsByClinicIdAndSpecialtyId(clinicId, specialtyId)) {
            clinicSpecialtyRepository.save(
                ClinicSpecialty(
                    id = ClinicSpecialtyId(clinicId, specialtyId),
                    clinic = clinic,
                    specialty = specialty,
                    durationMinutes = request.durationMinutes
                )
            )
        }
        return SpecialtyResponse(specialtyId, specialty.name, request.durationMinutes)
    }

    fun updateSpecialtyDuration(
        userEmail: String,
        specialtyId: UUID,
        request: UpdateSpecialtyDurationRequest
    ): SpecialtyResponse {
        val clinic = getClinic(userEmail)
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        val relation = clinicSpecialtyRepository.findAllByClinicId(clinicId)
            .firstOrNull { it.specialty?.id == specialtyId }
            ?: throw ResourceNotFoundException("Service is not associated with this clinic")

        relation.durationMinutes = request.durationMinutes
        val saved = clinicSpecialtyRepository.save(relation)
        val specialty = saved.specialty
            ?: throw ResourceNotFoundException("Service not found")

        val validSpecialtyId = specialty.id ?: throw IllegalStateException("Specialty missing ID")
        return SpecialtyResponse(validSpecialtyId, specialty.name, saved.durationMinutes)
    }

    fun removeSpecialty(userEmail: String, name: String) {//uncheck
        val clinic = getClinic(userEmail)
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        val specialty = specialtyRepository.findByNameIgnoreCase(name.trim()) ?: return
        val specialtyId = specialty.id ?: return

        clinicSpecialtyRepository.deleteByClinicIdAndSpecialtyId(clinicId, specialtyId)
    }

    fun deleteSpecialtyPermanently(userEmail: String, name: String) {
        val clinic = getClinic(userEmail)
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        val specialty = specialtyRepository.findByNameIgnoreCase(name.trim()) ?: return
        val specialtyId = specialty.id ?: return

        clinicSpecialtyRepository.deleteByClinicIdAndSpecialtyId(clinicId, specialtyId)
        if (!clinicSpecialtyRepository.existsBySpecialtyId(specialtyId)) {
            specialtyRepository.delete(specialty)
        }
    }

    // =========================================================================
    // 3. INSURANCE COMPANIES
    // =========================================================================

    @Transactional(readOnly = true)
    fun getInsuranceCompanies(userEmail: String): List<InsuranceCompanyResponse> {
        val clinic = getClinic(userEmail)
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        return insuranceCompanyRepository.findAllByClinicIdOrderByNameAsc(clinicId)
            .map { it.toResponse() }
    }

    fun addInsuranceCompany(userEmail: String, request: InsuranceCompanyRequest): InsuranceCompanyResponse {
        val clinic = getClinic(userEmail)

        val insuranceCompany = insuranceCompanyRepository.save(
            InsuranceCompany(
                clinic = clinic,
                name = request.name.trim(),
                coverageTier = request.coverageTier?.trim(),
                copay = request.copay?.trim(),
                phone = request.phone?.trim(),
                portalUrl = request.portalUrl?.trim(),
                instantPreApproval = request.instantPreApproval,
                network = request.network?.trim(),
                code = request.code?.trim(),
                badgeBg = request.badgeBg?.trim(),
                badgeText = request.badgeText?.trim(),
                directBillingType = request.directBillingType?.trim(),
                status = request.status?.trim()
            )
        )
        return insuranceCompany.toResponse()
    }

    fun updateInsuranceCompany(
        userEmail: String,
        insuranceId: UUID,
        request: InsuranceCompanyRequest
    ): InsuranceCompanyResponse {
        val clinic = getClinic(userEmail)
        val insuranceCompany = insuranceCompanyRepository.findById(insuranceId)
            .orElseThrow { ResourceNotFoundException("Insurance company not found with ID: $insuranceId") }

        if (insuranceCompany.clinic?.id != clinic.id) {
            throw ResourceNotFoundException("Insurance company not found in your clinic")
        }

        insuranceCompany.name = request.name.trim()
        insuranceCompany.coverageTier = request.coverageTier?.trim()
        insuranceCompany.copay = request.copay?.trim()
        insuranceCompany.phone = request.phone?.trim()
        insuranceCompany.portalUrl = request.portalUrl?.trim()
        insuranceCompany.instantPreApproval = request.instantPreApproval
        insuranceCompany.network = request.network?.trim()
        insuranceCompany.code = request.code?.trim()
        insuranceCompany.badgeBg = request.badgeBg?.trim()
        insuranceCompany.badgeText = request.badgeText?.trim()
        insuranceCompany.directBillingType = request.directBillingType?.trim()
        insuranceCompany.status = request.status?.trim()

        return insuranceCompanyRepository.save(insuranceCompany).toResponse()
    }

    fun deleteInsuranceCompany(userEmail: String, insuranceId: UUID) {
        val clinic = getClinic(userEmail)
        val insuranceCompany = insuranceCompanyRepository.findById(insuranceId)
            .orElseThrow { ResourceNotFoundException("Insurance company not found with ID: $insuranceId") }

        if (insuranceCompany.clinic?.id != clinic.id) {
            throw ResourceNotFoundException("Insurance company not found in your clinic")
        }

        insuranceCompanyRepository.delete(insuranceCompany)
    }

    // =========================================================================
    // 4. REVIEWS
    // =========================================================================

    @Transactional(readOnly = true)
    fun getReviews(userEmail: String, doctorId: UUID?, rating: Int?): List<ReviewResponse> {
        val clinic = getClinic(userEmail)
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        if (rating != null && rating !in 1..5) {
            throw AppException("Rating must be between 1 and 5")
        }

        return reviewRepository.findAllByClinicIdOrderByCreatedAtDesc(clinicId).asSequence()
            .filter { doctorId == null || it.doctor?.id == doctorId }
            .filter { rating == null || it.rating == rating }
            .mapNotNull { it.toResponse() }
            .toList()
    }

    fun replyToReview(userEmail: String, reviewId: UUID, request: ReviewReplyRequest): ReviewResponse {
        val clinic = getClinic(userEmail)
        val review = reviewRepository.findById(reviewId)
            .orElseThrow { ResourceNotFoundException("Review not found") }

        if (review.clinic?.id != clinic.id) {
            throw AppException("You cannot reply to this review")
        }

        review.reply = request.reply.trim()
        review.replyAt = Instant.now()
        val saved = reviewRepository.save(review)
        return saved.toResponse() ?: throw IllegalStateException("Failed to map review response")
    }

    // =========================================================================
    // 5. DOCTORS MANAGEMENT
    // =========================================================================

    @Transactional(readOnly = true)
    fun getDoctors(clinicEmail: String): List<DoctorResponse> {
        val clinic = getClinic(clinicEmail)
        val clinicUserId = clinic.user?.id ?: throw IllegalStateException("Clinic missing linked user")

        return clinicDoctorRepository.findAllByClinic_Id(clinicUserId)
            .mapNotNull { it.doctor?.toDoctorResponse() }
    }

    fun addDoctor(clinicEmail: String, request: AddDoctorRequest): DoctorResponse {
        val clinic = getClinic(clinicEmail)
        val clinicUser = clinic.user ?: throw IllegalStateException("Clinic missing linked user")
        val clinicUserId = clinicUser.id ?: throw IllegalStateException("Clinic user missing ID")

        if (userRepository.existsByEmail(request.email.trim())) {
            throw DuplicateResourceException("User with email '${request.email}' already exists")
        }

        val encodedPassword = passwordEncoder.encode(request.password)!!//can never be null

        val doctorUser = userRepository.save(
            User(
                fullName = request.fullName.trim(),
                city = request.city,
                email = request.email.trim().lowercase(),
                password = encodedPassword,
                role = Role.DOCTOR,
                isActive = true,
                bio = request.bio?.trim(),
                specialty = request.specialty?.trim()
            )
        )

        val doctorId = doctorUser.id ?: throw IllegalStateException("Created doctor missing ID")

        val clinicDoctor = ClinicDoctor(
            id = ClinicDoctorId(doctorUserId = doctorId, clinicUserId = clinicUserId),
            doctor = doctorUser,
            clinic = clinicUser
        )
        clinicDoctorRepository.save(clinicDoctor)

        return doctorUser.toDoctorResponse()
    }

    fun updateDoctor(clinicEmail: String, doctorUserId: UUID, request: UpdateDoctorRequest): DoctorResponse {
        val clinic = getClinic(clinicEmail)
        val clinicUserId = clinic.user?.id ?: throw IllegalStateException("Clinic missing linked user")

        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinicUserId, doctorUserId)) {
            throw ResourceNotFoundException("Doctor not found in your clinic catalog")
        }

        val doctorUser = userRepository.findById(doctorUserId)
            .orElseThrow { ResourceNotFoundException("Doctor user not found with ID: $doctorUserId") }

        val newEmail = request.email.trim().lowercase()
        if (newEmail != doctorUser.email.lowercase()) {
            if (userRepository.existsByEmail(newEmail)) {
                throw DuplicateResourceException("User with email '${request.email}' already exists")
            }
            doctorUser.email = newEmail
        }

        doctorUser.fullName = request.fullName.trim()
        if (request.city != null) {
            doctorUser.city = request.city
        }
        doctorUser.bio = request.bio?.trim()
        doctorUser.specialty = request.specialty?.trim()

        return userRepository.save(doctorUser).toDoctorResponse()
    }

    fun toggleDoctorStatus(clinicEmail: String, doctorUserId: UUID): DoctorResponse {
        val clinic = getClinic(clinicEmail)
        val clinicUserId = clinic.user?.id ?: throw IllegalStateException("Clinic missing linked user")

        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinicUserId, doctorUserId)) {
            throw ResourceNotFoundException("Doctor not found in your clinic catalog")
        }

        val doctorUser = userRepository.findById(doctorUserId)
            .orElseThrow { ResourceNotFoundException("Doctor user not found with ID: $doctorUserId") }

        doctorUser.isActive = !doctorUser.isActive
        return userRepository.save(doctorUser).toDoctorResponse()
    }

    fun deleteDoctor(clinicEmail: String, doctorUserId: UUID) {
        val clinic = getClinic(clinicEmail)
        val clinicUserId = clinic.user?.id ?: throw IllegalStateException("Clinic missing linked user")
        val clinicId = clinic.id ?: throw IllegalStateException("Clinic missing ID")

        val clinicDoctorId = ClinicDoctorId(doctorUserId = doctorUserId, clinicUserId = clinicUserId)

        if (!clinicDoctorRepository.existsById(clinicDoctorId)) {
            throw ResourceNotFoundException("Doctor not found in your clinic catalog")
        }

        val now = Instant.now()
        val allDoctorAppointments = appointmentRepository.findAllByClinic_IdAndDoctor_Id(clinicUserId, doctorUserId)

        allDoctorAppointments.forEach { appointment ->
            val appDate = appointment.appointmentDate
            if (appDate != null && appDate >= now &&
                (appointment.status == AppointmentStatus.PENDING || appointment.status == AppointmentStatus.CONFIRMED)
            ) {
                appointment.status = AppointmentStatus.CANCELLED
                appointment.bookingKey = null
                appointment.cancellationReason = "تم إلغاء الموعد لأن الطبيب لم يعد موجوداً في هذه العيادة"
            }
            appointment.schedule = null
        }

        if (allDoctorAppointments.isNotEmpty()) {
            appointmentRepository.saveAllAndFlush(allDoctorAppointments)
        }

        scheduleRepository.deleteByClinicIdAndDoctor_Id(clinicId, doctorUserId)
        clinicDoctorRepository.deleteById(clinicDoctorId)
    }

    // =========================================================================
    // 6. HELPER FUNCTIONS & MAPPERS
    // =========================================================================

    private fun getClinic(email: String): Clinic {//unified fetch for clinics
        return clinicRepository.findByUserEmail(email)
            .orElseThrow { ResourceNotFoundException("Clinic profile not found for email: $email") }
    }
//.toresponse to show what the frontend will receive
    private fun Clinic.toResponse(): ClinicProfileResponse {
        val clinicId = this.id ?: throw IllegalStateException("Clinic missing ID")
        val linkedUser = this.user ?: throw IllegalStateException("Clinic missing linked user")
        val userId = linkedUser.id ?: throw IllegalStateException("Linked user missing ID")

        return ClinicProfileResponse(
            id = clinicId,
            userId = userId,
            clinicName = this.clinicName,
            phoneNumber = this.phoneNumber,
            socialLinks = this.socialLinks,
            detailedAddress = this.detailedAddress,
            workingHours = this.workingHours,
            checkingFee = this.checkingFee,
            rating = this.rating,
            description = this.description,
            city = linkedUser.city,
            applicationStatus = this.applicationStatus,
            rejectionReason = this.rejectionReason
        )
    }

    private fun User.toDoctorResponse(): DoctorResponse {
        val userId = this.id ?: throw IllegalStateException("Doctor user missing ID")
        return DoctorResponse(
            doctorUserId = userId,
            fullName = this.fullName,
            email = this.email,
            city = this.city,
            role = this.role,
            isActive = this.isActive,
            bio = this.bio,
            specialty = this.specialty
        )
    }

    private fun InsuranceCompany.toResponse(): InsuranceCompanyResponse {
        val insuranceId = this.id ?: throw IllegalStateException("InsuranceCompany missing ID")
        return InsuranceCompanyResponse(
            id = insuranceId,
            name = this.name,
            coverageTier = this.coverageTier,
            copay = this.copay,
            phone = this.phone,
            portalUrl = this.portalUrl,
            instantPreApproval = this.instantPreApproval,
            network = this.network,
            code = this.code,
            badgeBg = this.badgeBg,
            badgeText = this.badgeText,
            directBillingType = this.directBillingType,
            status = this.status
        )
    }

    private fun Review.toResponse(): ReviewResponse? {
        val reviewId = this.id ?: return null
        val appointmentId = this.appointment?.id ?: return null
        val patient = this.patient ?: return null
        val patientId = patient.id ?: return null
        val doctor = this.doctor ?: return null
        val doctorId = doctor.id ?: return null

        return ReviewResponse(
            reviewId = reviewId,
            appointmentId = appointmentId,
            patientId = patientId,
            patientName = patient.fullName,
            doctorId = doctorId,
            doctorName = doctor.fullName,
            rating = this.rating,
            comment = this.comment,
            reply = this.reply,
            replyAt = this.replyAt?.atZone(zoneIdUtc)?.toOffsetDateTime(),
            createdAt = this.createdAt.atZone(zoneIdUtc).toOffsetDateTime()
        )
    }
}