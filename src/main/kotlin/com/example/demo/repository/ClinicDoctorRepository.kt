package com.example.demo.repository

import com.example.demo.model.ClinicDoctor
import com.example.demo.model.ClinicDoctorId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ClinicDoctorRepository : JpaRepository<ClinicDoctor, ClinicDoctorId> {
    fun findAllByClinic_Id(clinicUserId: UUID): List<ClinicDoctor>
    fun existsByClinic_IdAndDoctor_Id(clinicUserId: UUID, doctorUserId: UUID): Boolean
}
