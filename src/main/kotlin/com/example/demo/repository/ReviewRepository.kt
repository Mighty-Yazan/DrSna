package com.example.demo.repository

import com.example.demo.model.Review
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ReviewRepository : JpaRepository<Review, UUID> {
    fun existsByAppointmentId(appointmentId: UUID): Boolean
    fun findAllByClinicId(clinicId: UUID): List<Review>
    fun findAllByClinicIdOrderByCreatedAtDesc(clinicId: UUID): List<Review>
    fun findAllByDoctorIdAndRating(doctorId: UUID, rating: Int): List<Review>
}