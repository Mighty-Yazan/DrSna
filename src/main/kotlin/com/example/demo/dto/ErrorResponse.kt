package com.example.demo.dto

import java.time.Instant

data class ApiErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val message: String?,
    val path: String, // لمعرفة الرابط الذي حدث فيه الخطأ
    val validationErrors: List<String>? = null // يكون null في حال لم يكن الخطأ بسبب التحقق
)