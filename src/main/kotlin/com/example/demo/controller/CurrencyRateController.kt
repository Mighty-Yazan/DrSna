package com.example.demo.controller

import com.example.demo.dto.FxRateResponse
import com.example.demo.model.CurrencyCode
import com.example.demo.service.CurrencyRateService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/api/v1/super-admin/fx-rates")
class CurrencyRateController(
    private val currencyRateService: CurrencyRateService
) {

    // GET Endpoint: جلب جميع أسعار الصرف للشاشة الرئيسية
    @GetMapping
    fun getAllFxRates(): ResponseEntity<List<FxRateResponse>> {
        val rates = currencyRateService.getAllFxRates()
        return ResponseEntity.ok(rates)
    }

    // PUT Endpoint: تحديث سعر عملة معينة (مثال: /api/v1/super-admin/fx-rates/USD?rate=0.71)
    @PutMapping("/{code}")
    fun updateExchangeRate(
        @PathVariable code: CurrencyCode,
        @RequestParam rate: BigDecimal
    ): ResponseEntity<FxRateResponse> {
        val updatedRate = currencyRateService.updateExchangeRate(code, rate)
        return ResponseEntity.ok(updatedRate)
    }
}