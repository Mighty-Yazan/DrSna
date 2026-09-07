package com.example.demo.repository

import com.example.demo.model.AdminActivity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminActivityRepository : JpaRepository<AdminActivity, UUID> {
    fun findTop20ByOrderByCreatedAtDesc(): List<AdminActivity>
}