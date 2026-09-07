package com.example.demo.service

import com.example.demo.dto.SystemSettingDto
import com.example.demo.model.SystemSetting
import com.example.demo.repository.SystemSettingRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class SystemSettingService(
    private val systemSettingRepository: SystemSettingRepository
) {

    private val commissionKey = "DEFAULT_COMMISSION_RATE"

    // جلب نسبة العمولة الافتراضية للحسابات
    fun getDefaultCommissionRate(): BigDecimal {
        return systemSettingRepository.findByKey(commissionKey)
            .map { BigDecimal(it.value) }
            .orElse(BigDecimal("0.10"))
    }

    // تحديث نسبة العمولة الافتراضية
    fun updateDefaultCommissionRate(newRate: BigDecimal): SystemSettingDto {
        val setting = systemSettingRepository.findByKey(commissionKey)
            .orElseGet { SystemSetting(key = commissionKey, value = "0.10") }

        setting.value = newRate.toString()
        systemSettingRepository.save(setting)

        return SystemSettingDto(defaultCommissionRate = newRate)
    }
}