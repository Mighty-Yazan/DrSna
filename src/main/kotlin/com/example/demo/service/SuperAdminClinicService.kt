package com.example.demo.service

import com.example.demo.dto.ClinicManagementDto
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.UserRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class SuperAdminClinicService(
    private val clinicRepository: ClinicRepository,
    private val userRepository: UserRepository
) {

    //  جلب العيادات المعلقة بانتظار الموافقة (isActive = false)
    fun getPendingClinics(): List<ClinicManagementDto> {
        return clinicRepository.findAllByUser_IsActive(false).map { mapToDto(it) }
    }

    //  القبول أو الرفض (تغيير حالة isActive)
    fun updateClinicStatus(clinicUserId: UUID, approve: Boolean): ClinicManagementDto {
        val user = userRepository.findById(clinicUserId)
            .orElseThrow { NoSuchElementException("User not found with id: $clinicUserId") }

        user.isActive = approve
        userRepository.save(user)

        val clinic = clinicRepository.findByUser(user)
            .orElseThrow { NoSuchElementException("Clinic details not found for user: $clinicUserId") }

        return mapToDto(clinic)
    }

    //  عرض كافة العيادات النشطة مع دعم البحث برمز أو اسم العيادة
    fun getActiveClinics(searchQuery: String?): List<ClinicManagementDto> {
        val clinics = if (!searchQuery.isNullOrBlank()) {
            clinicRepository.searchClinicsByStatusAndQuery(isActive = true, query = searchQuery.trim())
        } else {
            clinicRepository.findAllByUser_IsActive(true)
        }
        return clinics.map { mapToDto(it) }
    }

    //  تعديل نسبة العمولة المخصصة لعيادة معينة
    fun updateClinicCommissionRate(clinicUserId: UUID, newRate: BigDecimal): ClinicManagementDto {
        val user = userRepository.findById(clinicUserId)
            .orElseThrow { NoSuchElementException("User not found with id: $clinicUserId") }

        val clinic = clinicRepository.findByUser(user)
            .orElseThrow { NoSuchElementException("Clinic details not found for user: $clinicUserId") }

        clinic.commissionRate = newRate
        clinicRepository.save(clinic)

        return mapToDto(clinic)
    }

    // دالة واحدة فقط لتحويل الكائن لـ DTO تشمل جميع الحقول
    private fun mapToDto(clinic: com.example.demo.model.Clinic): ClinicManagementDto {
        val user = clinic.user
        return ClinicManagementDto(
            clinicUserId = user?.id ?: UUID.randomUUID(),
            ownerName = user?.fullName ?: "",
            email = user?.email ?: "",
            clinicName = clinic.clinicName,
            phoneNumber = clinic.phoneNumber,
            checkingFee = clinic.checkingFee,
            commissionRate = clinic.commissionRate,
            isActive = user?.isActive ?: false,
            licenseNumber = user?.clinicLicenseNumber
        )
    }
}