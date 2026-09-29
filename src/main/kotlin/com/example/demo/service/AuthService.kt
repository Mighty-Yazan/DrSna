package com.example.demo.service

import com.example.demo.dto.*
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.exception.InvalidCredentialsException
import com.example.demo.exception.PasswordMismatchException
import com.example.demo.exception.ResourceNotFoundException
import com.example.demo.model.Clinic
import com.example.demo.model.ClinicApplicationStatus
import com.example.demo.model.Role
import com.example.demo.model.User
import com.example.demo.repository.UserRepository
import com.example.demo.security.SessionRevocationService
import com.example.demo.security.TokenBlacklistService
import com.example.demo.security.TokenService
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
@Transactional
class AuthService(
    private val userRepository: UserRepository,
    private val clinicRepository: com.example.demo.repository.ClinicRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenService: TokenService,
    private val tokenBlacklistService: TokenBlacklistService,
    private val sessionRevocationService: SessionRevocationService
) {

    // =========================================================================
    // REGISTER PATIENT
    // =========================================================================

    fun registerUser(
        request: UserRegisterRequest
    ): AuthResponse {

        if (request.password != request.confirmPassword) {
            throw PasswordMismatchException()
        }

        val normalizedEmail =
            request.email
                .trim()
                .lowercase()

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw DuplicateResourceException(
                "Email is already registered: $normalizedEmail"
            )
        }

        val newUser =
            User(
                fullName =
                    request.fullName.trim(),
                email =
                    normalizedEmail,
                password =
                    passwordEncoder.encode(
                        request.password
                    )!!,
                city =
                    request.city,
                role =
                    Role.PATIENT
            )

        val savedUser =
            userRepository.save(
                newUser
            )

        return AuthResponse(
            message =
                "User registered successfully",
            userId =
                savedUser.id,
            email =
                savedUser.email,
            role =
                savedUser.role
        )
    }

    // =========================================================================
    // REGISTER CLINIC
    // =========================================================================

    fun registerClinic(
        request: ClinicRegisterRequest
    ): AuthResponse {

        if (request.password != request.confirmPassword) {
            throw PasswordMismatchException()
        }

        val normalizedEmail =
            request.email
                .trim()
                .lowercase()

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw DuplicateResourceException(
                "Email is already registered: $normalizedEmail"
            )
        }

        val licenseNumber =
            request.clinicLicenseNumber.trim()

        if (
            userRepository
                .existsByClinicLicenseNumber(
                    licenseNumber
                )
        ) {
            throw DuplicateResourceException(
                "Clinic license number is already registered: $licenseNumber"
            )
        }

        val newUser =
            User(
                fullName =
                    request.clinicName.trim(),
                email =
                    normalizedEmail,
                password =
                    passwordEncoder.encode(
                        request.password
                    )!!,
                city =
                    request.city,
                role =
                    Role.CLINIC,
                clinicLicenseNumber =
                    licenseNumber
            )

        val savedUser =
            userRepository.save(
                newUser
            )

        clinicRepository.save(
            Clinic(
                user =
                    savedUser,
                clinicName =
                    savedUser.fullName,
                applicationStatus =
                    ClinicApplicationStatus.PENDING
            )
        )

        return AuthResponse(
            message =
                "Clinic registered successfully",
            userId =
                savedUser.id,
            email =
                savedUser.email,
            role =
                savedUser.role
        )
    }

    // =========================================================================
    // REGISTER ADMIN
    // =========================================================================

    fun registerAdmin(
        request: AdminRegisterRequest
    ): AuthResponse {

        if (request.password != request.confirmPassword) {
            throw PasswordMismatchException()
        }

        val normalizedEmail =
            request.email
                .trim()
                .lowercase()

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw DuplicateResourceException(
                "Email is already registered: $normalizedEmail"
            )
        }

        val newAdmin =
            User(
                fullName =
                    request.fullName.trim(),
                email =
                    normalizedEmail,
                password =
                    passwordEncoder.encode(
                        request.password
                    )!!,
                city =
                    request.city,
                role =
                    Role.ADMIN,
                isActive =
                    false
            )

        val savedAdmin =
            userRepository.save(
                newAdmin
            )

        return AuthResponse(
            message =
                "Admin registered successfully",
            userId =
                savedAdmin.id,
            email =
                savedAdmin.email,
            role =
                savedAdmin.role
        )
    }

    // =========================================================================
    // LOGIN
    // =========================================================================

    fun login(
        request: LoginRequest
    ): Pair<LoginResponse, String> {

        val normalizedEmail =
            request.email
                .trim()
                .lowercase()

        val user =
            userRepository
                .findByEmailForUpdate(
                    normalizedEmail
                )
                .orElseThrow {
                    InvalidCredentialsException(
                        "Invalid email or password"
                    )
                }

        /*
         * Verify the password before exposing
         * account/application-specific information.
         */
        if (
            !passwordEncoder.matches(
                request.password,
                user.password
            )
        ) {
            throw InvalidCredentialsException(
                "Invalid email or password"
            )
        }

        // ---------------------------------------------------------------------
        // ADMIN
        // ---------------------------------------------------------------------

        if (user.role == Role.ADMIN) {

            return issueLoginSession(
                user
            )
        }

        // ---------------------------------------------------------------------
        // PATIENT / DOCTOR
        // ---------------------------------------------------------------------

        if (!user.isActive) {
            throw InvalidCredentialsException(
                "Account is inactive"
            )
        }

        // ---------------------------------------------------------------------
        // CLINIC APPLICATION STATUS
        // ---------------------------------------------------------------------

        if (user.role == Role.CLINIC) {

            val clinic =
                clinicRepository
                    .findByUserEmail(
                        normalizedEmail
                    )
                    .orElseThrow {
                        IllegalStateException(
                            "Clinic profile not found for user: $normalizedEmail"
                        )
                    }

            when (clinic.applicationStatus) {

                ClinicApplicationStatus.PENDING -> {

                    /*
                     * Pending clinics receive no session.
                     */
                    throw InvalidCredentialsException(
                        "Clinic application is pending admin approval."
                    )
                }

                ClinicApplicationStatus.REJECTED -> {

                    /*
                     * Rejected clinics receive no session.
                     */
                    val reason =
                        clinic.rejectionReason
                            ?.trim()
                            ?.takeIf {
                                it.isNotBlank()
                            }

                    throw InvalidCredentialsException(
                        if (reason != null) {
                            "Clinic application was rejected: $reason"
                        } else {
                            "Clinic application was rejected."
                        }
                    )
                }

                ClinicApplicationStatus.REMOVED -> {

                    /*
                     * Removed clinics are permanently unavailable
                     * for normal clinic authentication.
                     *
                     * No Access Token.
                     * No Refresh Token.
                     */
                    throw InvalidCredentialsException(
                        "Clinic has been removed from the platform."
                    )
                }

                ClinicApplicationStatus.APPROVED -> {

                    /*
                     * Only APPROVED clinics may receive
                     * Access + Refresh tokens.
                     */
                }
            }
        }

        return issueLoginSession(
            user
        )
    }

    // =========================================================================
    // CREATE LOGIN SESSION
    // =========================================================================

    private fun issueLoginSession(
        user: User
    ): Pair<LoginResponse, String> {

        /*
         * Every successful login creates a separate refresh session.
         */
        val createdRefreshSession =
            tokenService.createRefreshSession(
                user
            )

        val refreshSession = createdRefreshSession.session

        val accessToken =
            tokenService.generateAccessToken(
                user,
                refreshSession
            )

        val loginResponse =
            LoginResponse(
                token =
                    accessToken,
                userId =
                    user.id,
                email =
                    user.email,
                role =
                    user.role,
                isActive =
                    user.isActive
            )

        return Pair(
            loginResponse,
            createdRefreshSession.rawToken
        )
    }

    // =========================================================================
    // REFRESH
    // =========================================================================

    fun refresh(
        refreshTokenString: String?
    ): Pair<LoginResponse, String> {

        if (refreshTokenString.isNullOrBlank()) {
            throw ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Refresh token is missing"
            )
        }

        val result =
            tokenService
                .rotateRefreshToken(
                    refreshTokenString
                )
                ?: throw ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid or expired refresh token"
                )

        val newRefreshTokenString =
            result.first

        val newRefreshToken =
            result.second

        val user =
            newRefreshToken.user

        val newAccessToken =
            tokenService.generateAccessToken(
                user,
                newRefreshToken
            )

        val loginResponse =
            LoginResponse(
                token =
                    newAccessToken,
                userId =
                    user.id,
                email =
                    user.email,
                role =
                    user.role,
                isActive =
                    user.isActive
            )

        return Pair(
            loginResponse,
            newRefreshTokenString
        )
    }

    // =========================================================================
    // GET PROFILE
    // =========================================================================

    fun getProfile(
        email: String
    ): UserProfileResponse {

        val user =
            userRepository
                .findByEmail(email)
                .orElseThrow {
                    ResourceNotFoundException(
                        "User not found with email: $email"
                    )
                }

        return UserProfileResponse(
            userId =
                user.id,
            fullName =
                user.fullName,
            email =
                user.email,
            phoneNumber =
                user.phoneNumber,
            city =
                user.city,
            role =
                user.role,
            clinicLicenseNumber =
                user.clinicLicenseNumber,
            bio =
                user.bio,
            specialty =
                user.specialty
        )
    }

    // =========================================================================
    // UPDATE PATIENT / DOCTOR PROFILE
    // =========================================================================

    fun updateProfile(
        email: String,
        request: UpdateProfileRequest
    ): ProfileUpdateResponse {

        val user =
            userRepository
                .findByEmailForUpdate(email)
                .orElseThrow {
                    ResourceNotFoundException(
                        "User not found with email: $email"
                    )
                }

        /*
         * Verify the current password before any
         * profile/security-sensitive change.
         */
        if (
            !passwordEncoder.matches(
                request.currentPassword,
                user.password
            )
        ) {
            throw IllegalArgumentException(
                "Incorrect current password"
            )
        }

        user.fullName =
            request.fullName.trim()

        user.city =
            request.city

        user.phoneNumber =
            request.phoneNumber?.trim()

        val passwordChanged =
            !request.newPassword
                .isNullOrBlank()

        if (passwordChanged) {

            if (
                request.newPassword !=
                request.confirmPassword
            ) {
                throw PasswordMismatchException()
            }

            user.password =
                passwordEncoder.encode(
                    request.newPassword
                )!!
        }

        val newEmail =
            request.email
                .trim()
                .lowercase()

        val emailChanged =
            newEmail != user.email

        if (
            emailChanged &&
            userRepository.existsByEmail(
                newEmail
            )
        ) {
            throw IllegalArgumentException(
                "Email is already in use"
            )
        }

        user.email =
            newEmail

        /*
         * Email is currently the JWT subject.
         * Password changes are also security-sensitive.
         */
        val securitySensitiveChange =
            passwordChanged ||
                    emailChanged

        if (securitySensitiveChange) {

            sessionRevocationService
                .revokeAllSessions(
                    user
                )
        }

        val savedUser =
            userRepository.save(
                user
            )

        /*
         * After revoking every previous session,
         * create one new session for the current device.
         */
        val newRefreshSession =
            if (securitySensitiveChange) {

                tokenService.createRefreshSession(
                    savedUser
                )

            } else {
                null
            }

        val newAccessToken =
            newRefreshSession?.let {
                tokenService.generateAccessToken(
                    savedUser,
                    it.session
                )
            }

        val newRefreshToken = newRefreshSession?.rawToken

        return ProfileUpdateResponse(
            userId =
                savedUser.id,
            fullName =
                savedUser.fullName,
            email =
                savedUser.email,
            phoneNumber =
                savedUser.phoneNumber,
            city =
                savedUser.city,
            role =
                savedUser.role,
            clinicLicenseNumber =
                savedUser.clinicLicenseNumber,
            bio =
                savedUser.bio,
            specialty =
                savedUser.specialty,
            token =
                newAccessToken,
            refreshToken =
                newRefreshToken,
            isActive =
                savedUser.isActive
        )
    }

    // =========================================================================
    // LOGOUT
    // =========================================================================

    fun logout(
        jti: String,
        expiresAtEpochSecond: Long,
        sessionId: UUID?
    ) {

        val expiresAt =
            java.time.Instant.ofEpochSecond(
                expiresAtEpochSecond
            )

        tokenBlacklistService.blacklist(
            jti,
            expiresAt
        )

        if (sessionId != null) {
            tokenService.revokeRefreshSession(
                sessionId
            )
        }
    }

    // =========================================================================
    // ADMIN PASSWORD CHANGE
    // =========================================================================

    fun changePassword(
        email: String,
        request: ChangePasswordRequest
    ): PasswordChangeResponse {

        if (
            request.newPassword !=
            request.confirmPassword
        ) {
            throw PasswordMismatchException()
        }

        val user =
            userRepository
                .findByEmailForUpdate(email)
                .orElseThrow {
                    ResourceNotFoundException(
                        "User not found: $email"
                    )
                }

        user.password =
            passwordEncoder.encode(
                request.newPassword
            )!!

        /*
         * Required for the initial Admin
         * first-login password setup.
         */
        user.isActive =
            true

        /*
         * Revoke all old sessions.
         */
        sessionRevocationService
            .revokeAllSessions(
                user
            )

        val savedUser =
            userRepository.save(
                user
            )

        /*
         * Create the new session for the current device.
         */
        val createdRefreshSession =
            tokenService.createRefreshSession(
                savedUser
            )

        val refreshSession = createdRefreshSession.session

        val newAccessToken =
            tokenService.generateAccessToken(
                savedUser,
                refreshSession
            )

        return PasswordChangeResponse(
            message =
                "Password changed successfully. All previous sessions were revoked.",
            token =
                newAccessToken,
            userId =
                savedUser.id,
            email =
                savedUser.email,
            role =
                savedUser.role,
            isActive =
                savedUser.isActive,
            refreshToken =
                createdRefreshSession.rawToken
        )
    }
}