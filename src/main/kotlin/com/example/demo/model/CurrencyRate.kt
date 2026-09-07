package com.example.demo.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "currency_rates")
class CurrencyRate(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // تخزين رمز العملة القادم من الـ Enum كـ STRING في قاعدة البيانات
    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 10)
    val code: CurrencyCode,

    @Column(name = "name", nullable = false, length = 50)
    val name: String,

    @Column(name = "symbol", nullable = false, length = 10)
    val symbol: String,

    @Column(name = "exchange_rate", nullable = false, precision = 12, scale = 6)
    var exchangeRate: BigDecimal, // السعر المالي المتغير (يعدله الأدمن)

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now() // تاريخ التحديث المطابق للجدول وشاشة FX Rates
)