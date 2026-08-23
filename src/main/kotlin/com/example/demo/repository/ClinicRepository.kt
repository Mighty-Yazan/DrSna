package com.example.demo.repository

import com.example.demo.model.Clinic
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ClinicRepository : JpaRepository<Clinic, UUID> {
    fun findByUserId(userId: UUID): Optional<Clinic>
    fun findByUserEmail(email: String): Optional<Clinic>
    fun existsByUserId(userId: UUID): Boolean
}