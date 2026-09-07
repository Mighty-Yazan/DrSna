package com.example.demo.dto

import java.math.BigDecimal

data class MonthlyRevenueChartDto(
    val yearMonth: String,      // اسم الشهر والسنة (مثال: "2026-04" أو "APRIL")
    val totalRevenue: BigDecimal // مجموع العمولات لهذا الشهر
)