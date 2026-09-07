package com.example.demo.repository

import com.example.demo.model.InsuranceCompany
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface InsuranceCompanyRepository : JpaRepository<InsuranceCompany, UUID> {
    fun findAllByClinicIdOrderByNameAsc(clinicId: UUID): List<InsuranceCompany>
    fun deleteAllByClinicId(clinicId: UUID)
}