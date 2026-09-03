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
    private val appointmentRepository: AppointmentRepository,
    private val scheduleRepository: ScheduleRepository
) {
    // ── CLINIC PROFILE ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getProfile(userEmail: String): ClinicProfileResponse {
        val clinic = getOrCreateClinic(userEmail)
        return clinic.toResponse()
    }

    fun updateProfile(userEmail: String, request: ClinicProfileRequest): ClinicProfileResponse {
        val clinic = getOrCreateClinic(userEmail)

        clinic.clinicName = request.clinicName.trim()
        clinic.phoneNumber = request.phoneNumber?.trim()
        clinic.detailedAddress = request.detailedAddress?.trim()
        clinic.socialLinks = request.socialLinks?.trim()
        clinic.workingHours = request.workingHours?.trim()
        clinic.checkingFee = request.checkingFee
        clinic.description = request.description?.trim()

        if (clinic.user != null) {
            clinic.user!!.fullName = request.clinicName.trim()
            if (request.city != null) {
                clinic.user!!.city = request.city
            }
            userRepository.save(clinic.user!!)
        }

        val updatedClinic = clinicRepository.save(clinic)
        return updatedClinic.toResponse()
    }




    fun addSpecialty(userEmail: String, request: SpecialtyRequest): SpecialtyResponse {
        val clinic = getOrCreateClinic(userEmail)
        val name = request.name.trim()
        val specialty = specialtyRepository.findByNameIgnoreCase(name)
            ?: specialtyRepository.save(Specialty(name = name))
        val clinicId = clinic.id!!
        val specialtyId = specialty.id!!
        if (!clinicSpecialtyRepository.existsByClinicIdAndSpecialtyId(clinicId, specialtyId)) {
            clinicSpecialtyRepository.save(
                ClinicSpecialty(
                    id = ClinicSpecialtyId(clinicId, specialtyId),
                    clinic = clinic,
                    specialty = specialty
                )
            )
        }
        return SpecialtyResponse(specialtyId, specialty.name)
    }

    @Transactional
    fun removeSpecialty(userEmail: String, name: String) {
        val clinic = getOrCreateClinic(userEmail)
        val specialty = specialtyRepository.findByNameIgnoreCase(name.trim()) ?: return
        clinicSpecialtyRepository.deleteByClinicIdAndSpecialtyId(clinic.id!!, specialty.id!!)
        
        // If no other clinic is using this specialty, completely remove it from the database
        if (!clinicSpecialtyRepository.existsBySpecialtyId(specialty.id!!)) {
            specialtyRepository.delete(specialty)
        }
    }

    @Transactional(readOnly = true)
    fun getSpecialties(userEmail: String): List<SpecialtyResponse> {
        val clinic = getOrCreateClinic(userEmail)
        return clinicSpecialtyRepository.findAllByClinicId(clinic.id!!).mapNotNull {
            val specialty = it.specialty ?: return@mapNotNull null
            SpecialtyResponse(specialty.id!!, specialty.name)
        }
    }

    @Transactional(readOnly = true)
    fun getReviews(userEmail: String, doctorId: UUID?, rating: Int?): List<ReviewResponse> {
        val clinic = getOrCreateClinic(userEmail)
        if (rating != null && rating !in 1..5) throw AppException("Rating must be between 1 and 5")
        return reviewRepository.findAllByClinicIdOrderByCreatedAtDesc(clinic.id!!).asSequence()
            .filter { doctorId == null || it.doctor?.id == doctorId }
            .filter { rating == null || it.rating == rating }
            .map { review ->
                ReviewResponse(review.id!!, review.appointment!!.id!!, review.patient!!.id!!, review.patient!!.fullName, review.doctor!!.id!!, review.doctor!!.fullName, review.rating, review.comment, review.reply, review.replyAt?.atZone(java.time.ZoneId.of("UTC"))?.toOffsetDateTime(), review.createdAt.atZone(java.time.ZoneId.of("UTC")).toOffsetDateTime())
            }.toList()
    }

    @Transactional
    fun replyToReview(userEmail: String, reviewId: UUID, request: ReviewReplyRequest): ReviewResponse {
        val clinic = getOrCreateClinic(userEmail)
        val review = reviewRepository.findById(reviewId).orElseThrow { ResourceNotFoundException("Review not found") }
        if (review.clinic?.id != clinic.id) throw AppException("You cannot reply to this review")
        review.reply = request.reply.trim()
        review.replyAt = java.time.Instant.now()
        val saved = reviewRepository.save(review)
        return ReviewResponse(saved.id!!, saved.appointment!!.id!!, saved.patient!!.id!!, saved.patient!!.fullName, saved.doctor!!.id!!, saved.doctor!!.fullName, saved.rating, saved.comment, saved.reply, saved.replyAt?.atZone(java.time.ZoneId.of("UTC"))?.toOffsetDateTime(), saved.createdAt.atZone(java.time.ZoneId.of("UTC")).toOffsetDateTime())
    }

    // ── DOCTORS MANAGEMENT ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getDoctors(clinicEmail: String): List<DoctorResponse> {
        val clinic = getOrCreateClinic(clinicEmail)
        val clinicUserId = clinic.user!!.id!!

        return clinicDoctorRepository.findAllByClinic_Id(clinicUserId)
            .map { cd -> cd.doctor!!.toDoctorResponse() }
    }

    fun addDoctor(clinicEmail: String, request: AddDoctorRequest): DoctorResponse {
        // 1. التأكد من وجود العيادة وحفظها أولاً لتفادي خطأ Foreign Key
        val clinic = getOrCreateClinic(clinicEmail)
        val clinicUser = clinic.user ?: throw IllegalStateException("Clinic missing linked user")

        if (userRepository.existsByEmail(request.email.trim())) {
            throw DuplicateResourceException("User with email '${request.email}' already exists")
        }

        // 2. إنشاء حساب الطبيب وتشفير الباسورد
        val doctorUser = userRepository.save(
            User(
                fullName = request.fullName.trim(),
                city = request.city,
                email = request.email.trim().lowercase(),
                password = passwordEncoder.encode(request.password)!!,
                role = Role.DOCTOR,
                isActive = true,
                bio = request.bio?.trim(),
                specialty = request.specialty?.trim()
            )
        )

        // 3. ربطه بالعيادة في جدول clinic_doctors
        val clinicDoctor = ClinicDoctor(
            id = ClinicDoctorId(doctorUserId = doctorUser.id!!, clinicUserId = clinicUser.id!!),
            doctor = doctorUser,
            clinic = clinicUser
        )
        clinicDoctorRepository.save(clinicDoctor)

        return doctorUser.toDoctorResponse()
    }

    fun updateDoctor(clinicEmail: String, doctorUserId: UUID, request: UpdateDoctorRequest): DoctorResponse {
        val clinic = getOrCreateClinic(clinicEmail)
        val clinicUserId = clinic.user!!.id!!

        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinicUserId, doctorUserId)) {
            throw ResourceNotFoundException("Doctor not found in your clinic catalog")
        }

        val doctorUser = userRepository.findById(doctorUserId)
            .orElseThrow { ResourceNotFoundException("Doctor user not found with ID: $doctorUserId") }

        doctorUser.fullName = request.fullName.trim()
        if (request.city != null) {
            doctorUser.city = request.city
        }
        doctorUser.bio = request.bio?.trim()
        doctorUser.specialty = request.specialty?.trim()

        val updatedDoctor = userRepository.save(doctorUser)
        return updatedDoctor.toDoctorResponse()
    }

    @Transactional
    fun toggleDoctorStatus(clinicEmail: String, doctorUserId: UUID): DoctorResponse {
        val clinic = getOrCreateClinic(clinicEmail)
        val clinicUserId = clinic.user!!.id!!

        if (!clinicDoctorRepository.existsByClinic_IdAndDoctor_Id(clinicUserId, doctorUserId)) {
            throw ResourceNotFoundException("Doctor not found in your clinic catalog")
        }

        val doctorUser = userRepository.findById(doctorUserId)
            .orElseThrow { ResourceNotFoundException("Doctor user not found with ID: $doctorUserId") }

        doctorUser.isActive = !doctorUser.isActive
        val saved = userRepository.save(doctorUser)

        return saved.toDoctorResponse()
    }

    fun deleteDoctor(
        clinicEmail: String,
        doctorUserId: UUID
    ) {

        val clinic =
            getOrCreateClinic(clinicEmail)

        val clinicUserId =
            clinic.user!!.id!!

        val clinicDoctorId =
            ClinicDoctorId(
                doctorUserId = doctorUserId,
                clinicUserId = clinicUserId
            )

        if (
            !clinicDoctorRepository
                .existsById(clinicDoctorId)
        ) {
            throw ResourceNotFoundException(
                "Doctor not found in your clinic catalog"
            )
        }

        /*
         * First, remove the relationship between the doctor and this clinic.
         */
        clinicDoctorRepository.deleteById(
            clinicDoctorId
        )
        
        /*
         * Delete all appointments and schedules associated with the doctor
         */
        appointmentRepository.deleteAllByDoctor_Id(doctorUserId)
        scheduleRepository.deleteAllByDoctor_Id(doctorUserId)

        /*
         * Then delete the actual user record from the database, 
         * as requested by the frontend team.
         */
        userRepository.deleteById(doctorUserId)
    }

    // ── REUSABLE HELPERS ────────────────────────────────────────────────

    private fun getOrCreateClinic(userEmail: String): Clinic {
        return clinicRepository.findByUserEmail(userEmail).orElseGet {
            val user = userRepository.findByEmail(userEmail)
                .orElseThrow { ResourceNotFoundException("User not found with email: $userEmail") }

            clinicRepository.save(
                Clinic(
                    user = user,
                    clinicName = user.fullName
                )
            )
        }
    }

    private fun Clinic.toResponse() = ClinicProfileResponse(
        id = this.id!!,
        userId = this.user?.id ?: throw IllegalStateException("Clinic missing linked user"),
        clinicName = this.clinicName,
        phoneNumber = this.phoneNumber,
        socialLinks = this.socialLinks,
        detailedAddress = this.detailedAddress,
        workingHours = this.workingHours,
        checkingFee = this.checkingFee,
        rating = this.rating,
        description = this.description,
        city = this.user?.city ?: throw IllegalStateException("Clinic missing linked user city")
    )


    private fun User.toDoctorResponse() = DoctorResponse(
        doctorUserId = this.id!!,
        fullName = this.fullName,
        email = this.email,
        city = this.city,
        role = this.role,
        isActive = this.isActive,
        bio = this.bio,
        specialty = this.specialty
    )
}