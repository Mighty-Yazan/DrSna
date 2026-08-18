package com.example.demo.controller

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api")
class HealthController(private val jdbcTemplate: JdbcTemplate) {

    @GetMapping("/health")
    fun checkHealth(): Map<String, Any> {
        return try {
            jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            mapOf(
                "status" to "UP",
                "database" to "CONNECTED",
                "timestamp" to LocalDateTime.now().toString()
            )
        } catch (e: Exception) {
            mapOf(
                "status" to "DOWN",
                "database" to "DISCONNECTED",
                "timestamp" to LocalDateTime.now().toString(),
                "error" to (e.message ?: "Unknown database error")
            )
        }
    }
}