package com.example.demo.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

//clinic dto
data class ClinicProfileRequest(
  @field:NotBlank(message="Clinic name cannot be blank")
  @field:Size(max=100,message="clinic name must not exceed 100 characters")
  val clinicName:String,

  @field:Size(max = 10, message = "Phone number must not exceed 10 characters")
  val phoneNumber: String?,

  val socialLinks: String? ,

  @field:Size(max = 255, message = "Detailed address must not exceed 255 characters")
  val detailedAddress: String?,

  @field:Size(max = 255, message = "Working hours must not exceed 255 characters")
  val workingHours: String?,

  @field:DecimalMin(value = "0.0", inclusive = true, message = "Checking fee cannot be negative")
  val checkingFee: BigDecimal?,

  val description:String?,
)
data class ClinicProfileResponse(
    val id:UUID,
    val userId:UUID,
    val clinicName: String,
    val phoneNumber: String?,
    val socialLinks: String?,
    val detailedAddress: String?,
    val workingHours: String?,
    val checkingFee: BigDecimal?,
    val rating: BigDecimal,
    val description: String?
)


//service dto
data class ServicesRequest(
   @field:NotBlank(message="Clinic service cannot be blank")
   @field:Size(max=100, message="Clinic service must not exceed 100 characters")
   val serviceName:String,

   val descriptionOfService:String?
)
data class ServicesResponse(
    val id:UUID,
    val clinicId: UUID,
    val serviceName: String,
    val descriptionOfService: String?,
)