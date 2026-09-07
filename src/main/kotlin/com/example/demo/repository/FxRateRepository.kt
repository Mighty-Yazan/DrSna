package com.example.demo.repository

import com.example.demo.model.FxRate
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FxRateRepository : JpaRepository<FxRate, UUID> {
    fun findByBaseCurrencyAndQuoteCurrency(baseCurrency: String, quoteCurrency: String): FxRate?
}