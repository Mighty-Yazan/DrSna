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
        val clinic = clinicRepository.findByUserEmail(email).orElse(null) ?: return false

        if (clinic.applicationStatus != ClinicApplicationStatus.APPROVED) {
            return false
        }

        if (clinic.user?.isActive != true) {
            return false
        }

        return true
    }

    /**
     * Checks if the currently authenticated doctor belongs to an APPROVED and active clinic.
     * Used to restrict doctors from managing schedules/appointments if their clinic is suspended.
     */
    fun isDoctorClinicApprovedAndActive(doctorEmail: String): Boolean {
        val doctorUser = userRepository.findByEmail(doctorEmail).orElse(null) ?: return false

        val clinicDoctors = clinicDoctorRepository.findAllByDoctor_Id(doctorUser.id!!)
        if (clinicDoctors.isEmpty()) {
            return false
        }

        // A doctor typically belongs to one clinic in this system.
        // We check if at least one associated clinic is approved and active.
        return clinicDoctors.any { clinicDoc ->
            val clinicUser = clinicDoc.clinic ?: return@any false
            // Optimize: Check if the user is active BEFORE hitting the ClinicRepository
            if (!clinicUser.isActive) return@any false

            val clinicOpt = clinicRepository.findByUserId(clinicUser.id!!)
            if (clinicOpt.isPresent) {
                clinicOpt.get().applicationStatus == ClinicApplicationStatus.APPROVED
            } else {
                false
            }
        }
    }
}
