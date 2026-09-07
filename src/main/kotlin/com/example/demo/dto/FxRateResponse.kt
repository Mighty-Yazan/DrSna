package com.example.demo.dto

import com.example.demo.model.CurrencyCode
import java.math.BigDecimal
import java.time.Instant

data class FxRateResponse(
    val code: CurrencyCode,      // رمز العملة من الـ Enum
    val symbol: String,          // مثل $ أو د.أ
    val exchangeRate: BigDecimal,// سعر الصرف مقابل العملة الأساسية
    val updatedAt: Instant       // تاريخ ووقت التحديث
)