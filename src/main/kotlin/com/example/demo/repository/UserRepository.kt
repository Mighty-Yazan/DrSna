package com.example.demo.repository

import com.example.demo.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID
import com.example.demo.model.Role
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Query

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): Optional<User>
    fun existsByEmail(email: String): Boolean
    fun existsByClinicLicenseNumber(clinicLicenseNumber: String): Boolean
    fun countByRoleAndIsActive(role: Role, isActive: Boolean): Long

    // جلب قائمة المستخدمين حسب الدور
    fun findAllByRole(role: Role): List<User>
    // البحث عن مستخدم بالاسم أو البريد الإلكتروني ودوره
    @Query("""
        SELECT u FROM User u 
        WHERE u.role = :role 
          AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) 
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    fun searchUsersByRoleAndQuery(
        @Param("role") role: Role,
        @Param("query") query: String
    ): List<User>
    // البحث الشامل لجميع المستخدمين بالاسم أو البريد الإلكتروني
    @Query("""
        SELECT u FROM User u 
        WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) 
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
    """)
    fun searchAllUsersByQuery(@Param("query") query: String): List<User>
}