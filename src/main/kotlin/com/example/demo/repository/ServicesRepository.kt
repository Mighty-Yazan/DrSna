package com.example.demo.repository

import com.example.demo.model.Services
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ServicesRepository : JpaRepository<Services, UUID> {
    fun findAllByClinicId(clinicId: UUID): List<Services>
    fun existsByClinicIdAndServiceNameIgnoreCase(clinicId: UUID, serviceName: String): Boolean
}