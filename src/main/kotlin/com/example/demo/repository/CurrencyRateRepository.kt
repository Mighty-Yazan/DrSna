package com.example.demo.repository

import com.example.demo.model.CurrencyCode
import com.example.demo.model.CurrencyRate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface CurrencyRateRepository : JpaRepository<CurrencyRate, Long> {

    // استعلام للبحث عن سعر صرف عملة معينة بناءً على الـ Enum
    fun findByCode(code: CurrencyCode): Optional<CurrencyRate>

}