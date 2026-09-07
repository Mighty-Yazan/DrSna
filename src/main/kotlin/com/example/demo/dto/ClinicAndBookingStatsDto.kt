package com.example.demo.dto

data class ClinicAndBookingStatsDto(
    val activeClinicsCount: Long,     // عدد العيادات النشطة
    val pendingApprovalsCount: Long,  // عدد العيادات المعلقة
    val bookingsThisMonthCount: Long  // عدد الحجوزات للشهر الحالي
)