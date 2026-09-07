package com.example.demo.controller

import com.example.demo.dto.ClinicAndBookingStatsDto
import com.example.demo.dto.CommissionRevenueDto
import com.example.demo.service.SuperAdminDashboardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import com.example.demo.dto.MonthlyRevenueChartDto
import com.example.demo.dto.FinancialReportSummaryDto
import org.springframework.security.access.prepost.PreAuthorize

@RestController
@RequestMapping("/api/v1/super-admin/dashboard")
@PreAuthorize("hasRole('SUPER_ADMIN')")
class SuperAdminDashboardController(
    private val dashboardService: SuperAdminDashboardService
) {

    // 1. Endpoint الخاص بـ Task 1 (حساب أرباح العمولات ونسبة التغير)
    @GetMapping("/commission-revenue")
    fun getCommissionRevenue(): ResponseEntity<CommissionRevenueDto> {
        val response = dashboardService.getCommissionRevenue()
        return ResponseEntity.ok(response)
    }

    // 2. Endpoint الخاص بـ (أعداد العيادات النشطة، المعلقة، وحجوزات الشهر)
    @GetMapping("/stats")
    fun getClinicAndBookingStats(): ResponseEntity<ClinicAndBookingStatsDto> {
        val stats = dashboardService.getClinicAndBookingStats()
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/revenue-chart")
    fun getCommissionRevenueLast6Months(): ResponseEntity<List<MonthlyRevenueChartDto>> {
        val chartData = dashboardService.getCommissionRevenueLast6Months()
        return ResponseEntity.ok(chartData)
    }

    @GetMapping("/financial-report")
    fun getFinancialReport(): ResponseEntity<FinancialReportSummaryDto> {
        val report = dashboardService.getFinancialReport()
        return ResponseEntity.ok(report)
    }
}
