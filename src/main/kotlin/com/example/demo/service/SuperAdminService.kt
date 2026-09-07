package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.exception.AppException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.*
import com.example.demo.repository.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.util.UUID

@Service
@Transactional
class SuperAdminService(
    private val clinicRepository: ClinicRepository,
    private val userRepository: UserRepository,
    private val appointmentRepository: AppointmentRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository,
    private val clinicSpecialtyRepository: ClinicSpecialtyRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reviewRepository: ReviewRepository,
    private val insuranceCompanyRepository: InsuranceCompanyRepository,
    private val favoriteDoctorRepository: FavoriteDoctorRepository,
    private val fxRateRepository: FxRateRepository,
    private val adminActivityRepository: AdminActivityRepository
) {
    private val zoneId = ZoneOffset.UTC

    @Transactional(readOnly = true)
    fun getDashboard(): DashboardSummaryResponse {
        val now = Instant.now()
        val currentMonth = YearMonth.now(zoneId)
        val previousMonth = currentMonth.minusMonths(1)

        val currentRevenue = commissionRevenue(currentMonth)
        val previousRevenue = commissionRevenue(previousMonth)
        val change = if (previousRevenue.compareTo(BigDecimal.ZERO) == 0) {
            if (currentRevenue.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else BigDecimal.valueOf(100)
        } else {
            currentRevenue.subtract(previousRevenue)
                .divide(previousRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
        }

        val pending = getClinicsInternal(null, null, ClinicApplicationStatus.PENDING, true)
        val sixMonths = (5 downTo 0).map { offset ->
            val month = currentMonth.minusMonths(offset.toLong())
            MonthlyCommissionRevenueResponse(month.toString(), commissionRevenue(month))
        }

        val fxRates = fxRateRepository.findAll().map { it.toResponse() }
        val lastFxUpdate = fxRates.maxOfOrNull { it.updatedAt }

        return DashboardSummaryResponse(
            commissionRevenueCurrentMonth = currentRevenue,
            commissionRevenueChangePercent = change,
            activeClinics = clinicRepository.countActiveByApplicationStatus(ClinicApplicationStatus.APPROVED),
            pendingApprovals = pending.size.toLong(),
            pendingClinics = pending,
            bookingsThisMonth = appointmentRepository.findAllByAppointmentDateBetween(
                monthStart(currentMonth), monthStart(currentMonth.plusMonths(1))
            ).count { it.status != AppointmentStatus.CANCELLED }.toLong(),
            commissionRevenueLast6Months = sixMonths,
            fxRates = fxRates,
            fxLastUpdated = lastFxUpdate,
            recentActivity = adminActivityRepository.findTop20ByOrderByCreatedAtDesc().map { it.toResponse() },
            topClinicsByCommission = topClinicsByCommission()
        )
    }

    @Transactional(readOnly = true)
    fun getClinics(
        search: String?,
        city: City?,
        status: ClinicApplicationStatus?,
        active: Boolean?
    ): List<ClinicAdminListItem> = getClinicsInternal(search, city, status, active)

    @Transactional(readOnly = true)
    fun getClinicCount(): Long = clinicRepository.count()

    @Transactional(readOnly = true)
    fun getFxRates(): List<FxRateResponse> = fxRateRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun getRecentActivities(): List<AdminActivityResponse> =
        adminActivityRepository.findTop20ByOrderByCreatedAtDesc().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun getTopClinics(): List<TopClinicCommissionResponse> = topClinicsByCommission()

    @Transactional(readOnly = true)
    fun getLast6MonthsRevenue(): List<MonthlyCommissionRevenueResponse> {
        val currentMonth = YearMonth.now(zoneId)
        return (5 downTo 0).map { offset ->
            val month = currentMonth.minusMonths(offset.toLong())
            MonthlyCommissionRevenueResponse(month.toString(), commissionRevenue(month))
        }
    }

    @Transactional(readOnly = true)
    fun getPendingClinics(): List<ClinicAdminListItem> =
        getClinicsInternal(null, null, ClinicApplicationStatus.PENDING, true)

    @Transactional(readOnly = true)
    fun getClinicReview(clinicId: UUID): ClinicApplicationReviewResponse {
        val clinic = getClinic(clinicId)
        val clinicUser = clinic.user ?: throw AppException("Clinic is missing its submitting user")
        val clinicUserId = clinicUser.id ?: throw AppException("Clinic user has no ID")

        val doctors = clinicDoctorRepository.findAllByClinic_Id(clinicUserId).mapNotNull { relation ->
            relation.doctor?.let { doctor ->
                ClinicDoctorReviewItem(
                    doctorUserId = doctor.id!!,
                    fullName = doctor.fullName,
                    email = doctor.email,
                    specialty = doctor.specialty,
                    isActive = doctor.isActive
                )
            }
        }

        val services = clinicSpecialtyRepository.findAllByClinicId(clinic.id!!).mapNotNull { relation ->
            val specialty = relation.specialty ?: return@mapNotNull null
            ClinicServiceReviewItem(
                specialtyId = specialty.id!!,
                name = specialty.name,
                durationMinutes = relation.durationMinutes
            )
        }

        return ClinicApplicationReviewResponse(
            clinicId = clinic.id!!,
            clinicInformation = ClinicInformationResponse(
                clinicName = clinic.clinicName,
                phoneNumber = clinic.phoneNumber,
                address = clinic.detailedAddress,
                city = clinicUser.city,
                workingHours = clinic.workingHours,
                checkingFee = clinic.checkingFee,
                rating = clinic.rating,
                description = clinic.description
            ),
            applicationStatus = clinic.applicationStatus,
            submissionDate = clinic.submittedAt(),
            submittingUser = SubmittingUserResponse(
                userId = clinicUser.id!!,
                fullName = clinicUser.fullName,
                email = clinicUser.email,
                city = clinicUser.city,
                role = clinicUser.role.name,
                createdAt = clinicUser.createdAt
            ),
            brand = clinic.brand,
            branches = clinic.branchCount,
            doctors = doctors,
            services = services,
            currency = clinic.currency,
            taxRegistration = clinic.taxRegistration,
            currentCommissionRate = clinic.commissionRate,
            overriddenCommissionRate = clinic.overriddenCommissionRate,
            effectiveCommissionRate = clinic.effectiveCommissionRate(),
            rejectionReason = clinic.rejectionReason
        )
    }

    fun approveClinic(adminEmail: String, clinicId: UUID): ClinicApplicationReviewResponse {
        val clinic = getClinic(clinicId)
        if (clinic.applicationStatus != ClinicApplicationStatus.PENDING) {
            throw AppException("Only PENDING clinic applications can be approved")
        }
        clinic.applicationStatus = ClinicApplicationStatus.APPROVED
        clinic.rejectionReason = null
        clinicRepository.save(clinic)
        logActivity(adminEmail, "APPROVE_CLINIC", "Clinic '${clinic.clinicName}' was approved", clinic.id)
        return getClinicReview(clinicId)
    }

    fun rejectClinic(adminEmail: String, clinicId: UUID, request: RejectClinicRequest): ClinicApplicationReviewResponse {
        val reason = request.reason.trim()
        if (reason.isBlank()) throw AppException("Rejection reason is required")

        val clinic = getClinic(clinicId)
        if (clinic.applicationStatus != ClinicApplicationStatus.PENDING) {
            throw AppException("Only PENDING clinic applications can be rejected")
        }
        clinic.applicationStatus = ClinicApplicationStatus.REJECTED
        clinic.rejectionReason = reason
        clinicRepository.save(clinic)
        logActivity(adminEmail, "REJECT_CLINIC", "Clinic '${clinic.clinicName}' was rejected: $reason", clinic.id)
        return getClinicReview(clinicId)
    }

    fun overrideCommission(
        adminEmail: String,
        clinicId: UUID,
        request: CommissionOverrideRequest
    ): ClinicApplicationReviewResponse {
        val clinic = getClinic(clinicId)
        if (clinic.applicationStatus != ClinicApplicationStatus.PENDING) {
            throw AppException("Commission override is available during PENDING review only")
        }
        clinic.overriddenCommissionRate = request.commissionRate.setScale(4, RoundingMode.HALF_UP)
        clinicRepository.save(clinic)
        logActivity(
            adminEmail,
            "OVERRIDE_COMMISSION",
            "Commission for clinic '${clinic.clinicName}' overridden to ${clinic.overriddenCommissionRate}%",
            clinic.id
        )
        return getClinicReview(clinicId)
    }

    fun upsertFxRate(adminEmail: String, request: UpsertFxRateRequest): FxRateResponse {
        val base = request.baseCurrency.trim().uppercase()
        val quote = request.quoteCurrency.trim().uppercase()
        if (base == quote) throw AppException("Base and quote currencies must be different")

        val rate = fxRateRepository.findByBaseCurrencyAndQuoteCurrency(base, quote)
            ?: FxRate(baseCurrency = base, quoteCurrency = quote)
        rate.rate = request.rate
        rate.updatedAt = Instant.now()
        val saved = fxRateRepository.save(rate)
        logActivity(adminEmail, "UPDATE_FX_RATE", "FX rate updated: $base/$quote = ${request.rate}", null)
        return saved.toResponse()
    }

    @Transactional
    fun removeClinic(adminEmail: String, clinicId: UUID) {
        val clinic = getClinic(clinicId)
        val clinicUser = clinic.user ?: throw AppException("Clinic is missing its user")
        val clinicUserId = clinicUser.id ?: throw AppException("Clinic user has no ID")

        val clinicDoctors = clinicDoctorRepository.findAllByClinic_Id(clinicUserId)
            .mapNotNull { it.doctor?.id }

        // Permanent delete: remove dependent records first.
        reviewRepository.deleteAllByClinicId(clinic.id!!)
        appointmentRepository.deleteAllByClinic_Id(clinic.id!!)
        insuranceCompanyRepository.deleteAllByClinicId(clinic.id!!)
        clinicSpecialtyRepository.deleteAllByClinicId(clinic.id!!)
        scheduleRepository.deleteAllByClinicId(clinic.id!!)
        clinicDoctorRepository.deleteAllByClinic_Id(clinicUserId)

        clinicDoctors.forEach { doctorId ->
            // Delete doctor only when no other clinic still owns/uses the doctor.
            if (clinicDoctorRepository.findAllByDoctor_Id(doctorId).isEmpty()) {
                favoriteDoctorRepository.deleteAllByDoctorId(doctorId)
                scheduleRepository.deleteAllByDoctor_Id(doctorId)
                appointmentRepository.deleteAllByDoctor_Id(doctorId)
                userRepository.deleteById(doctorId)
            }
        }

        val clinicName = clinic.clinicName
        clinicRepository.delete(clinic)
        userRepository.delete(clinicUser)
        logActivity(adminEmail, "REMOVE_CLINIC", "Clinic '$clinicName' was permanently removed", clinicId)
    }

    private fun getClinicsInternal(
        search: String?,
        city: City?,
        status: ClinicApplicationStatus?,
        active: Boolean?
    ): List<ClinicAdminListItem> {
        return clinicRepository.searchForAdmin(search?.trim()?.takeIf { it.isNotBlank() }, city, status, active)
            .map { clinic ->
                val clinicUserId = clinic.user?.id ?: throw AppException("Clinic missing linked user")
                ClinicAdminListItem(
                    clinicId = clinic.id!!,
                    clinicName = clinic.clinicName,
                    city = clinic.user!!.city,
                    numberOfBranches = clinic.branchCount,
                    numberOfDoctors = clinicDoctorRepository.countByClinic_Id(clinicUserId),
                    currency = clinic.currency,
                    submittedDate = clinic.submittedAt(),
                    applicationStatus = clinic.applicationStatus,
                    isActive = clinic.user!!.isActive,
                    rejectionReason = clinic.rejectionReason
                )
            }
    }

    private fun getClinic(clinicId: UUID): Clinic =
        clinicRepository.findDetailsById(clinicId)
            .orElseThrow { ResourceNotFoundException("Clinic not found with ID: $clinicId") }

    private fun commissionRevenue(month: YearMonth): BigDecimal {
        val appointments = appointmentRepository.findAllByAppointmentDateBetween(
            monthStart(month),
            monthStart(month.plusMonths(1))
        )
        return appointments
            .filter { it.status == AppointmentStatus.COMPLETED }
            .map { appointment ->
                val clinicUserId = appointment.clinic?.id ?: return@map BigDecimal.ZERO
                val clinic = clinicRepository.findByUserId(clinicUserId).orElse(null) ?: return@map BigDecimal.ZERO
                val fee = clinic.checkingFee ?: BigDecimal.ZERO
                fee.multiply(clinic.effectiveCommissionRate())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
            }
            .fold(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP)
    }

    private fun topClinicsByCommission(): List<TopClinicCommissionResponse> {
        val clinics = clinicRepository.findAll()
        val result = clinics.map { clinic ->
            val clinicUserId = clinic.user?.id ?: return@map null
            val commission = appointmentRepository.findAllByClinicIdAndAppointmentDateBetween(
                clinicUserId,
                Instant.EPOCH,
                Instant.now()
            ).filter { it.status == AppointmentStatus.COMPLETED }
                .fold(BigDecimal.ZERO) { total, _ ->
                    val fee = clinic.checkingFee ?: BigDecimal.ZERO
                    total.add(
                        fee.multiply(clinic.effectiveCommissionRate())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    )
                }
            TopClinicCommissionResponse(clinic.id!!, clinic.clinicName, commission.setScale(2, RoundingMode.HALF_UP), clinic.currency)
        }.filterNotNull()

        return result.sortedByDescending { it.commission }.take(10)
    }

    private fun monthStart(month: YearMonth): Instant = month.atDay(1).atStartOfDay(zoneId).toInstant()

    private fun logActivity(adminEmail: String, action: String, description: String, clinicId: UUID?) {
        val adminId = userRepository.findByEmail(adminEmail).map { it.id }.orElse(null)
        adminActivityRepository.save(
            AdminActivity(
                adminUserId = adminId,
                action = action,
                description = description,
                clinicId = clinicId
            )
        )
    }

    private fun FxRate.toResponse() = FxRateResponse(id!!, baseCurrency, quoteCurrency, rate, updatedAt)

    private fun AdminActivity.toResponse() = AdminActivityResponse(id!!, action, description, clinicId, createdAt)
}