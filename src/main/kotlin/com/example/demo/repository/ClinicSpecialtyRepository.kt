package com.example.demo.repository

import com.example.demo.model.ClinicSpecialty
import com.example.demo.model.ClinicSpecialtyId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ClinicSpecialtyRepository : JpaRepository<ClinicSpecialty, ClinicSpecialtyId> {
    fun findAllByClinicId(clinicId: UUID): List<ClinicSpecialty>
    fun existsByClinicIdAndSpecialtyId(clinicId: UUID, specialtyId: UUID): Boolean
    fun existsBySpecialtyId(specialtyId: UUID): Boolean
    fun deleteByClinicIdAndSpecialtyId(clinicId: UUID, specialtyId: UUID)
}