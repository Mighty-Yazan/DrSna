package com.example.demo.dto

import java.math.BigDecimal
import java.util.UUID

data class ClinicManagementDto(
    val clinicUserId: UUID,
    val ownerName: String,
    val email: String,
    val clinicName: String,
    val phoneNumber: String?,
    val checkingFee: BigDecimal?,
    val isActive: Boolean,
    val licenseNumber: String?,
    val commissionRate: BigDecimal, // إضافة نسبة العمولة هنا
)