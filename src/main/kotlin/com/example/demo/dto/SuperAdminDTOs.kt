package com.example.demo.dto

import com.example.demo.model.City
import com.example.demo.model.ClinicApplicationStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ClinicAdminListItem(
    val clinicId: UUID,
    val clinicName: String,
    val city: City,
    val numberOfBranches: Int,
    val numberOfDoctors: Long,
    val currency: String?,
    val submittedDate: Instant,
    val applicationStatus: ClinicApplicationStatus,
    val isActive: Boolean,
    val rejectionReason: String?
)

data class ClinicApplicationReviewResponse(
    val clinicId: UUID,
    val clinicInformation: ClinicInformationResponse,
    val applicationStatus: ClinicApplicationStatus,
    val submissionDate: Instant,
    val submittingUser: SubmittingUserResponse,
    val brand: String?,
    val branches: Int,
    val doctors: List<ClinicDoctorReviewItem>,
    val services: List<ClinicServiceReviewItem>,
    val currency: String?,
    val taxRegistration: String?,
    val currentCommissionRate: BigDecimal,
    val overriddenCommissionRate: BigDecimal?,
    val effectiveCommissionRate: BigDecimal,
    val rejectionReason: String?
)

data class ClinicInformationResponse(
    val clinicName: String,
    val phoneNumber: String?,
    val address: String?,
    val city: City,
    val workingHours: String?,
    val checkingFee: BigDecimal?,
    val rating: BigDecimal,
    val description: String?
)

data class SubmittingUserResponse(
    val userId: UUID,
    val fullName: String,
    val email: String,
    val city: City,
    val role: String,
    val createdAt: Instant
)

data class ClinicDoctorReviewItem(
    val doctorUserId: UUID,
    val fullName: String,
    val email: String,
    val specialty: String?,
    val isActive: Boolean
)

data class ClinicServiceReviewItem(
    val specialtyId: UUID,
    val name: String,
    val durationMinutes: Int
)

data class RejectClinicRequest(
    @field:NotBlank(message = "Rejection reason is required")
    @field:Size(max = 1000, message = "Rejection reason must not exceed 1000 characters")
    val reason: String
)

data class CommissionOverrideRequest(
    @field:NotNull(message = "Commission rate is required")
    @field:DecimalMin(value = "0.0", inclusive = true, message = "Commission rate cannot be negative")
    val commissionRate: BigDecimal
)

data class DashboardSummaryResponse(
    val commissionRevenueCurrentMonth: BigDecimal,
    val commissionRevenueChangePercent: BigDecimal,
    val activeClinics: Long,
    val pendingApprovals: Long,
    val pendingClinics: List<ClinicAdminListItem>,
    val bookingsThisMonth: Long,
    val commissionRevenueLast6Months: List<MonthlyCommissionRevenueResponse>,
    val fxRates: List<FxRateResponse>,
    val fxLastUpdated: Instant?,
    val recentActivity: List<AdminActivityResponse>,
    val topClinicsByCommission: List<TopClinicCommissionResponse>
)

data class MonthlyCommissionRevenueResponse(
    val month: String,
    val revenue: BigDecimal
)

data class FxRateResponse(
    val id: UUID,
    val baseCurrency: String,
    val quoteCurrency: String,
    val rate: BigDecimal,
    val updatedAt: Instant
)

data class UpsertFxRateRequest(
    @field:NotBlank(message = "Base currency is required")
    @field:Size(max = 10)
    val baseCurrency: String,
    @field:NotBlank(message = "Quote currency is required")
    @field:Size(max = 10)
    val quoteCurrency: String,
    @field:NotNull(message = "Rate is required")
    @field:DecimalMin(value = "0.0", inclusive = false, message = "Rate must be greater than zero")
    val rate: BigDecimal
)

data class AdminActivityResponse(
    val id: UUID,
    val action: String,
    val description: String,
    val clinicId: UUID?,
    val createdAt: Instant
)

data class TopClinicCommissionResponse(
    val clinicId: UUID,
    val clinicName: String,
    val commission: BigDecimal,
    val currency: String?
)