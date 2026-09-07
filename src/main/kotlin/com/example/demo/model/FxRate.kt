package com.example.demo.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "fx_rates",
    uniqueConstraints = [UniqueConstraint(name = "uk_fx_rate_currency_pair", columnNames = ["base_currency", "quote_currency"])]
)
class FxRate(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "fx_rate_id", updatable = false, nullable = false)
    var id: UUID? = null,

    @Column(name = "base_currency", nullable = false, length = 10)
    var baseCurrency: String = "",

    @Column(name = "quote_currency", nullable = false, length = 10)
    var quoteCurrency: String = "",

    @Column(name = "rate", nullable = false, precision = 18, scale = 8)
    var rate: BigDecimal = BigDecimal.ONE,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)