package com.example.demo.repository

import com.example.demo.model.FavoriteDoctor
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FavoriteDoctorRepository : JpaRepository<FavoriteDoctor, UUID> {
    fun findByPatientId(patientId: UUID): List<FavoriteDoctor>
    fun existsByPatientIdAndDoctorId(patientId: UUID, doctorId: UUID): Boolean
    fun deleteByPatientIdAndDoctorId(patientId: UUID, doctorId: UUID)
    fun deleteAllByDoctorId(doctorId: UUID)
}