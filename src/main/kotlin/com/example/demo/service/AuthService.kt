package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.exception.InvalidCredentialsException
import com.example.demo.exception.PasswordMismatchException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.Role
import com.example.demo.model.User
import com.example.demo.repository.UserRepository
import com.example.demo.security.TokenBlacklistService
import com.example.demo.security.TokenService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val tokenBlacklistService: TokenBlacklistService
) {

    fun registerUser(request: UserRegisterRequest): AuthResponse {
        if (request.password != request.confirmPassword) {
            throw PasswordMismatchException()
        }

        val normalizedEmail = request.email.trim().lowercase()

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw DuplicateResourceException("Email is already registered: $normalizedEmail")
        }

        val newUser = User(
            fullName = request.fullName.trim(),
            email = normalizedEmail,
            password = passwordEncoder.encode(request.password)!!,
            city = request.city,
            role = Role.PATIENT
        )

        val savedUser = userRepository.save(newUser)

        return AuthResponse(
            message = "User registered successfully",
            userId = savedUser.id,
            email = savedUser.email,
            role = savedUser.role
        )
    }

    fun registerClinic(request: ClinicRegisterRequest): AuthResponse {
        if (request.password != request.confirmPassword) {
            throw PasswordMismatchException()
        }

        val normalizedEmail = request.email.trim().lowercase()

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw DuplicateResourceException("Email is already registered: $normalizedEmail")
        }

        if (userRepository.existsByClinicLicenseNumber(request.clinicLicenseNumber.trim())) {
            throw DuplicateResourceException("Clinic license number is already registered: ${request.clinicLicenseNumber}")
        }

        val newUser = User(
            fullName = request.clinicName.trim(),
            email = normalizedEmail,
            password = passwordEncoder.encode(request.password)!!,
            city = request.city,
            role = Role.CLINIC,
            clinicLicenseNumber = request.clinicLicenseNumber.trim()
        )

        val savedUser = userRepository.save(newUser)

        return AuthResponse(
            message = "Clinic registered successfully",
            userId = savedUser.id,
            email = savedUser.email,
            role = savedUser.role
        )
    }

    fun login(request: LoginRequest): LoginResponse {
        val normalizedEmail = request.email.trim().lowercase()

        val user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow { InvalidCredentialsException("Invalid email or password") }

        if (!passwordEncoder.matches(request.password, user.password)) {
            throw InvalidCredentialsException("Invalid email or password")
        }

        val token = tokenService.generateToken(user)

        return LoginResponse(
            token = token,
            userId = user.id,
            email = user.email,
            role = user.role
        )
    }

    fun getProfile(email: String): UserProfileResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { ResourceNotFoundException("User not found with email: $email") }

        return UserProfileResponse(
            userId = user.id,
            fullName = user.fullName,
            email = user.email,
            city = user.city,
            role = user.role,
            clinicLicenseNumber = user.clinicLicenseNumber
        )
    }

    fun logout(jti: String, expiresAtEpochSecond: Long) {
        val expiresAt = java.time.Instant.ofEpochSecond(expiresAtEpochSecond)
        tokenBlacklistService.blacklist(jti, expiresAt)
    }
}