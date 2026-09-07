package com.example.demo.service

import com.example.demo.dto.ClinicAndBookingStatsDto
import com.example.demo.dto.CommissionRevenueDto
import com.example.demo.dto.MonthlyRevenueChartDto
import com.example.demo.model.Role
import com.example.demo.repository.AppointmentRepository
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.UserRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import com.example.demo.dto.FinancialReportSummaryDto
import com.example.demo.dto.AppointmentFinancialItemDto
import java.util.UUID

@Service
class SuperAdminDashboardService(
    private val appointmentRepository: AppointmentRepository,
    private val userRepository: UserRepository,
    private val clinicRepository: ClinicRepository
) {

    // نسبة العمولة الافتراضية للنظام (10%) كقيمة احتياطية
    private val defaultCommissionRate = BigDecimal("0.10")

    // --- حساب أرباح العمولات ونسبة التغير مقارنة بالشهر السابق ---
    fun getCommissionRevenue(): CommissionRevenueDto {
        val now = LocalDate.now()
        val currentMonth = YearMonth.from(now)
        val currentStart = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val currentEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC)

        val previousMonth = currentMonth.minusMonths(1)
        val previousStart = previousMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val previousEnd = previousMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC)

        val currentRevenue = calculateRevenueForPeriod(currentStart, currentEnd)
        val previousRevenue = calculateRevenueForPeriod(previousStart, previousEnd)

        // حساب نسبة التغير باستخدام دالة النسب المئوية المخصصة
        val percentageChange = calculatePercentageChange(currentRevenue, previousRevenue)

        return CommissionRevenueDto(
            currentMonthRevenue = currentRevenue.setScale(2, RoundingMode.HALF_UP),
            previousMonthRevenue = previousRevenue.setScale(2, RoundingMode.HALF_UP),
            percentageChange = percentageChange.toDouble()
        )
    }

    // --- حساب أعداد العيادات النشطة، المعلقة، وحجوزات الشهر ---
    fun getClinicAndBookingStats(): ClinicAndBookingStatsDto {
        val now = LocalDate.now()
        val currentMonth = YearMonth.from(now)
        val startOfMonth = currentMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        val endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC)

        val activeClinics = userRepository.countByRoleAndIsActive(Role.CLINIC, true)
        val pendingClinics = userRepository.countByRoleAndIsActive(Role.CLINIC, false)
        val monthBookings = appointmentRepository.countAppointmentsBetweenDates(startOfMonth, endOfMonth)

        return ClinicAndBookingStatsDto(
            activeClinicsCount = activeClinics,
            pendingApprovalsCount = pendingClinics,
            bookingsThisMonthCount = monthBookings
        )
    }

    // --- حساب العمولات لآخر 6 أشهر للعرض في الرسم البياني ---
    fun getCommissionRevenueLast6Months(): List<MonthlyRevenueChartDto> {
        val result = mutableListOf<MonthlyRevenueChartDto>()
        val currentMonth = YearMonth.now()

        for (i in 5 downTo 0) {
            val targetMonth = currentMonth.minusMonths(i.toLong())
            val startOfMonth = targetMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC)
            val endOfMonth = targetMonth.atEndOfMonth().atTime(23, 59, 59).toInstant(ZoneOffset.UTC)

            val monthTotalCommission = calculateRevenueForPeriod(startOfMonth, endOfMonth)

            result.add(
                MonthlyRevenueChartDto(
                    yearMonth = targetMonth.month.name,
                    totalRevenue = monthTotalCommission.setScale(2, RoundingMode.HALF_UP)
                )
            )
        }

        return result
    }

    // --- دالة حساب الإيرادات لفترة زمنية محددة ---
    private fun calculateRevenueForPeriod(start: Instant, end: Instant): BigDecimal {
        val completedAppointments = appointmentRepository.findAllCompletedAppointmentsBetweenDates(start, end)
        var totalCommission = BigDecimal.ZERO

        for (appointment in completedAppointments) {
            val clinicUser = appointment.clinic
            if (clinicUser != null) {
                val clinic = clinicRepository.findByUser(clinicUser).orElse(null)
                val fee = clinic?.checkingFee ?: BigDecimal.ZERO

                // استخدام نسبة العمولة الخاصة بالعيادة (أو 10% كقيمة احتياطية)
                val rate = clinic?.commissionRate ?: defaultCommissionRate

                val commission = fee.multiply(rate)
                totalCommission = totalCommission.add(commission)
            }
        }
        return totalCommission
    }

    // --- دالة منفصلة لحساب نسبة التغير المئوية مع تفادي القسمة على صفر ---
    private fun calculatePercentageChange(current: BigDecimal, previous: BigDecimal): BigDecimal {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return if (current.compareTo(BigDecimal.ZERO) > 0) BigDecimal("100.00") else BigDecimal.ZERO
        }
        return current.subtract(previous)
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
    }

    // جلب التقرير المالي الشامل للحجوزات المكتملة
    fun getFinancialReport(): FinancialReportSummaryDto {
        val completedAppointments = appointmentRepository.findAllByStatus(com.example.demo.model.AppointmentStatus.COMPLETED)

        var totalRevenue = BigDecimal.ZERO
        var totalCommission = BigDecimal.ZERO

        val items = completedAppointments.map { appointment ->
            val clinicUser = appointment.clinic
            val clinic = clinicUser?.let { clinicRepository.findByUser(it).orElse(null) }

            val fee = clinic?.checkingFee ?: BigDecimal.ZERO
            val rate = clinic?.commissionRate ?: defaultCommissionRate
            val commission = fee.multiply(rate)

            totalRevenue = totalRevenue.add(fee)
            totalCommission = totalCommission.add(commission)

            AppointmentFinancialItemDto(
                appointmentId = appointment.id ?: UUID.randomUUID(),
                clinicName = clinic?.clinicName ?: "Unknown Clinic",
                patientName = appointment.patient?.fullName ?: appointment.formPatientName ?: "Unknown Patient",
                appointmentDate = appointment.appointmentDate!!, // تم إزالة التكرار والاعتماد على !! لأن التاريخ مؤكد للمواعيد المكتملة
                checkingFee = fee,
                commissionRate = rate,
                platformCommission = commission
            )
        }

        return FinancialReportSummaryDto(
            totalCompletedAppointments = completedAppointments.size.toLong(),
            totalRevenueGenerated = totalRevenue.setScale(2, java.math.RoundingMode.HALF_UP),
            totalPlatformCommission = totalCommission.setScale(2, java.math.RoundingMode.HALF_UP),
            appointments = items
        )
    }
}