package com.example.demo.repository

import com.example.demo.model.SystemSetting
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SystemSettingRepository : JpaRepository<SystemSetting, String> {
    fun findByKey(key: String): Optional<SystemSetting>
}