package com.example.demo.repository

import com.example.demo.model.Specialty
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SpecialtyRepository : JpaRepository<Specialty, UUID> {
    fun findByNameIgnoreCase(name: String): Specialty?
    fun existsByNameIgnoreCase(name: String): Boolean
}