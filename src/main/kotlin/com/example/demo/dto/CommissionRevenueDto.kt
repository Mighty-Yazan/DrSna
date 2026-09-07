package com.example.demo.dto

import java.math.BigDecimal

data class CommissionRevenueDto(
    val currentMonthRevenue: BigDecimal?, // مجموع عمولات الشهر الحالي
    val previousMonthRevenue: BigDecimal?, // مجموع عمولات الشهر السابق
    val percentageChange: Double          // نسبة التغير المئوية
)