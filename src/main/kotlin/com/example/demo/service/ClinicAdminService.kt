package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.exception.AppException
import com.example.demo.model.Clinic
import com.example.demo.model.Services
import com.example.demo.repository.ClinicRepository
import com.example.demo.repository.ServicesRepository
import com.example.demo.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class ClinicAdminService(
    private val clinicRepository: ClinicRepository,
    private val servicesRepository: ServicesRepository,
    private val userRepository: UserRepository
) {


    // CLINIC


    @Transactional(readOnly = true)
    fun getProfile(userEmail: String): ClinicProfileResponse {
        val clinic = getOrCreateClinic(userEmail)
        return clinic.toResponse()
    }

    fun updateProfile(userEmail: String, request: ClinicProfileRequest): ClinicProfileResponse {
        val clinic = getOrCreateClinic(userEmail)

        clinic.clinicName = request.clinicName.trim()
        clinic.phoneNumber = request.phoneNumber?.trim()
        clinic.detailedAddress = request.detailedAddress?.trim()
        clinic.socialLinks = request.socialLinks?.trim()
        clinic.workingHours = request.workingHours?.trim()
        clinic.checkingFee = request.checkingFee
        clinic.description = request.description?.trim()

        val updatedClinic = clinicRepository.save(clinic)
        return updatedClinic.toResponse()
    }


    // SERVICES


    @Transactional(readOnly = true)
    fun getServices(userEmail: String): List<ServicesResponse> {
        val clinic = getOrCreateClinic(userEmail)
        return servicesRepository.findAllByClinicId(clinic.id!!)
            .map { it.toResponse() }
    }

    fun addService(userEmail: String, request: ServicesRequest): ServicesResponse {
        val clinic = getOrCreateClinic(userEmail)
        val trimmedServiceName = request.serviceName.trim()

        if (servicesRepository.existsByClinicIdAndServiceNameIgnoreCase(clinic.id!!, trimmedServiceName)) {
            throw DuplicateResourceException("A service named '$trimmedServiceName' already exists in your clinic catalog")
        }

        val service = Services(
            clinic = clinic,
            serviceName = trimmedServiceName,
            descriptionOfService = request.descriptionOfService?.trim()
        )

        val savedService = servicesRepository.save(service)
        return savedService.toResponse()
    }

    fun updateService(userEmail: String, serviceId: UUID, request: ServicesRequest): ServicesResponse {
        val clinic = getOrCreateClinic(userEmail)
        val service = servicesRepository.findById(serviceId)
            .orElseThrow { ResourceNotFoundException("Service not found with ID: $serviceId") }

        if (service.clinic?.id != clinic.id) {
            throw AppException("You do not have permission to modify this service")
        }

        val trimmedServiceName = request.serviceName.trim()
        if (!service.serviceName.equals(trimmedServiceName, ignoreCase = true) &&
            servicesRepository.existsByClinicIdAndServiceNameIgnoreCase(clinic.id!!, trimmedServiceName)
        ) {
            throw DuplicateResourceException("A service named '$trimmedServiceName' already exists in your clinic catalog")
        }

        service.serviceName = trimmedServiceName
        service.descriptionOfService = request.descriptionOfService?.trim()

        val updatedService = servicesRepository.save(service)
        return updatedService.toResponse()
    }

    fun deleteService(userEmail: String, serviceId: UUID) {
        val clinic = getOrCreateClinic(userEmail)
        val service = servicesRepository.findById(serviceId)
            .orElseThrow { ResourceNotFoundException("Service not found with ID: $serviceId") }

        if (service.clinic?.id != clinic.id) {
            throw AppException("You do not have permission to delete this service")
        }

        servicesRepository.delete(service)
    }


//reusuable functions

    private fun getOrCreateClinic(userEmail: String): Clinic {
        return clinicRepository.findByUserEmail(userEmail).orElseGet {
            val user = userRepository.findByEmail(userEmail)
                .orElseThrow { ResourceNotFoundException("User not found with email: $userEmail") }

            clinicRepository.save(
                Clinic(
                    user = user,
                    clinicName = user.fullName
                )
            )
        }
    }

    private fun Clinic.toResponse() = ClinicProfileResponse(
        id = this.id!!,
        userId = this.user?.id ?: throw IllegalStateException("Clinic missing linked user"),
        clinicName = this.clinicName,
        phoneNumber = this.phoneNumber,
        socialLinks = this.socialLinks,
        detailedAddress = this.detailedAddress,
        workingHours = this.workingHours,
        checkingFee = this.checkingFee,
        rating = this.rating,
        description = this.description
    )

    private fun Services.toResponse() = ServicesResponse(
        id = this.id!!,
        clinicId = this.clinic?.id ?: throw IllegalStateException("Service missing linked clinic"),
        serviceName = this.serviceName,
        descriptionOfService = this.descriptionOfService,
    )
}