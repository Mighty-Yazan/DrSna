package com.example.demo.security

import com.example.demo.exception.ClinicNotOperationalException
import com.example.demo.model.ClinicApplicationStatus
import com.example.demo.repository.ClinicDoctorRepository
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.UserRepository
import org.springframework.stereotype.Component

@Component("clinicSecurity")
class ClinicSecurity(
    private val clinicRepository: ClinicRepository,
    private val userRepository: UserRepository,
    private val clinicDoctorRepository: ClinicDoctorRepository
) {

    /**
     * Checks if the currently authenticated user (CLINIC role) has an APPROVED application status
     * and their user account is active.
     */
    fun isApprovedAndActive(email: String): Boolean {
        val clinic = clinicRepository.findByUserEmail(email)
            .orElseThrow { ClinicNotOperationalException("Clinic not found for email: $email") }

        if (clinic.applicationStatus != ClinicApplicationStatus.APPROVED) {
            throw ClinicNotOperationalException("Clinic application is ${clinic.applicationStatus}. Operational actions are restricted.")
        }

        if (clinic.user?.isActive != true) {
            throw ClinicNotOperationalException("Clinic account is deactivated. Operational actions are restricted.")
        }

        return true
    }

    /**
     * Checks if the currently authenticated doctor belongs to an APPROVED and active clinic.
     * Used to restrict doctors from managing schedules/appointments if their clinic is suspended.
     */
    fun isDoctorClinicApprovedAndActive(doctorEmail: String): Boolean {
        val doctorUser = userRepository.findByEmail(doctorEmail)
            .orElseThrow { ClinicNotOperationalException("Doctor not found for email: $doctorEmail") }

        val clinicDoctors = clinicDoctorRepository.findAllByDoctor_Id(doctorUser.id!!)
        if (clinicDoctors.isEmpty()) {
            throw ClinicNotOperationalException("Doctor is not associated with any clinic.")
        }

        // A doctor typically belongs to one clinic in this system.
        // We check if at least one associated clinic is approved and active.
        val hasApprovedClinic = clinicDoctors.any { clinicDoc ->
            val clinicUserId = clinicDoc.clinic?.id ?: return@any false
            val clinicOpt = clinicRepository.findByUserId(clinicUserId)
            
            if (clinicOpt.isPresent) {
                val clinic = clinicOpt.get()
                clinic.applicationStatus == ClinicApplicationStatus.APPROVED && clinic.user?.isActive == true
            } else {
                false
            }
        }

        if (!hasApprovedClinic) {
            throw ClinicNotOperationalException("The clinic you belong to is not approved or is inactive. Operational actions are restricted.")
        }

        return true
    }
}
