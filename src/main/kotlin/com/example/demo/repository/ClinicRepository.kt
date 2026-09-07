package com.example.demo.repository

import com.example.demo.model.Clinic
import com.example.demo.model.City
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID
import com.example.demo.model.User

@Repository
interface ClinicRepository : JpaRepository<Clinic, UUID> {

    fun findByUserId(userId: UUID): Optional<Clinic>

    fun findByUserEmail(email: String): Optional<Clinic>

    fun existsByUserId(userId: UUID): Boolean

    @Query("""
        select c
        from Clinic c
        join fetch c.user u
        where c.id = :clinicId
    """)
    fun findDetailsById(
        @Param("clinicId") clinicId: UUID
    ): Optional<Clinic>

    @Query("""
        select c
        from Clinic c
        join fetch c.user u
        where lower(c.clinicName) like lower(concat('%', coalesce(:name, ''), '%'))
          and (:city is null or u.city = :city)
        order by
            case when u.city = :patientCity then 0 else 1 end,
            c.rating desc nulls last,
            lower(c.clinicName) asc
    """)
    fun search(
        @Param("name") name: String?,
        @Param("city") city: City?,
        @Param("patientCity") patientCity: City
    ): List<Clinic>

    fun findByUser(user: User): Optional<Clinic>

    // جلب كافة العيادات بناءً على حالة تفعيل الحساب (isActive)
    fun findAllByUser_IsActive(isActive: Boolean): List<Clinic>

    // البحث عن عيادة حسب اسمها أو اسم المالك وحالتها
    @Query("""
        SELECT c FROM Clinic c 
        JOIN c.user u 
        WHERE u.isActive = :isActive 
          AND (LOWER(c.clinicName) LIKE LOWER(CONCAT('%', :query, '%')) 
               OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')))
    """)
    fun searchClinicsByStatusAndQuery(
        @Param("isActive") isActive: Boolean,
        @Param("query") query: String
    ): List<Clinic>
}