package com.example.demo.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class AppointmentFinancialItemDto(
    val appointmentId: UUID,
    val clinicName: String,
    val patientName: String,
    val appointmentDate: Instant,
    val checkingFee: BigDecimal,
    val commissionRate: BigDecimal,
    val platformCommission: BigDecimal
)

data class FinancialReportSummaryDto(
    val totalCompletedAppointments: Long,
    val totalRevenueGenerated: BigDecimal,
    val totalPlatformCommission: BigDecimal,
    val appointments: List<AppointmentFinancialItemDto>
)