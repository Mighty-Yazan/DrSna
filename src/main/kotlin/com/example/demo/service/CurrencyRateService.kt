package com.example.demo.service

import com.example.demo.dto.FxRateResponse
import com.example.demo.model.CurrencyCode
import com.example.demo.repository.CurrencyRateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@Service
class CurrencyRateService(
    private val currencyRateRepository: CurrencyRateRepository
) {

    // 1. جلب جميع أسعار الصرف في الـ Dashboard
    @Transactional(readOnly = true)
    fun getAllFxRates(): List<FxRateResponse> {
        return currencyRateRepository.findAll().map { rate ->
            FxRateResponse(
                code = rate.code,
                symbol = rate.symbol,
                exchangeRate = rate.exchangeRate,
                updatedAt = rate.updatedAt
            )
        }
    }

    // 2. تحديث سعر صرف عملة معينة مع تحديث الوقت تلقائياً
    @Transactional
    fun updateExchangeRate(code: CurrencyCode, newRate: BigDecimal): FxRateResponse {
        val currencyRate = currencyRateRepository.findByCode(code)
            .orElseThrow { NoSuchElementException("Currency not found: $code") }

        currencyRate.exchangeRate = newRate
        currencyRate.updatedAt = Instant.now() // تحديث تاريخ ووقت التعديل

        val updated = currencyRateRepository.save(currencyRate)

        return FxRateResponse(
            code = updated.code,
            symbol = updated.symbol,
            exchangeRate = updated.exchangeRate,
            updatedAt = updated.updatedAt
        )
    }
}