package com.example.demo.controller

import com.example.demo.dto.ClinicProfileRequest
import com.example.demo.dto.ClinicProfileResponse
import com.example.demo.dto.MessageResponse
import com.example.demo.dto.ServicesRequest
import com.example.demo.dto.ServicesResponse
import com.example.demo.service.ClinicAdminService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID
@RestController
@RequestMapping("/api/clinic")
@PreAuthorize("hasRole('CLINIC')")

class ClinicAdminController (private val clinicAdminService: ClinicAdminService,){
    // clinic profile endpoints
    @GetMapping("/profile")
    fun getProfile(authentication: Authentication): ResponseEntity<ClinicProfileResponse>{
        val profile=clinicAdminService.getProfile(authentication.name)
        return ResponseEntity.ok(profile)
    }
    @PutMapping("/profile")
    fun updateProfile(authentication: Authentication,@Valid@RequestBody request: ClinicProfileRequest): ResponseEntity<ClinicProfileResponse>{
        val updatedProfile=clinicAdminService.updateProfile(authentication.name,request)
         return ResponseEntity.ok(updatedProfile)
    }



// services endpoints
    @GetMapping("/services")
fun getServices(authentication: Authentication): ResponseEntity<List<ServicesResponse>>{
    val services=clinicAdminService.getServices(authentication.name)
    return ResponseEntity.ok(services)
}
    @PostMapping("/services")
    fun addService(
        authentication: Authentication,
        @Valid @RequestBody request: ServicesRequest
    ): ResponseEntity<ServicesResponse> {
        val createdService = clinicAdminService.addService(authentication.name, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdService)
    }
    @PutMapping("/services/{id}")
    fun updateService(
        authentication: Authentication,
        @PathVariable id: UUID,
        @Valid @RequestBody request: ServicesRequest
    ): ResponseEntity<ServicesResponse> {
        val updatedService = clinicAdminService.updateService(authentication.name, id, request)
        return ResponseEntity.ok(updatedService)
    }
    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteService(authentication: Authentication,@PathVariable id: UUID): ResponseEntity<MessageResponse>{
        clinicAdminService.deleteService(authentication.name, id)
        return ResponseEntity.ok(MessageResponse("Service deleted successfully"))

    }

}