package com.example.demo.config

import com.example.demo.model.City
import com.example.demo.model.Role
import com.example.demo.model.User
import com.example.demo.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    prefix = "admin.seed",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AdminDataSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,

    @Value("\${admin.seed.email:}")
    private val adminEmail: String,

    @Value("\${admin.seed.password:}")
    private val adminPassword: String,

    @Value("\${admin.seed.full-name:System Administrator}")
    private val adminFullName: String,

    @Value("\${admin.seed.city:AMMAN}")
    private val adminCity: String
) : CommandLineRunner {

    override fun run(vararg args: String) {
        val email = adminEmail.trim().lowercase()
        val rawPassword = adminPassword.trim()

        // 1. Fail-fast validation: Do not allow blank or insecure passwords when seeding is explicitly requested
        if (email.isBlank() || rawPassword.isBlank()) {
            throw IllegalStateException(
                "Admin bootstrapping was explicitly enabled (ADMIN_SEED_ENABLED=true), but ADMIN_SEED_EMAIL or ADMIN_SEED_PASSWORD is empty."
            )
        }

        if (rawPassword.equals("Admin123!", ignoreCase = true) || rawPassword.length < 8) {
            throw IllegalStateException(
                "Insecure password detected. Bootstrapped admin accounts cannot use repository-known passwords or passwords shorter than 8 characters."
            )
        }

        // 2. Idempotency: Do not duplicate admin if one already exists
        if (userRepository.existsByRole(Role.ADMIN)) {
            println("Admin seed skipped: an account with ROLE_ADMIN already exists.")
            return
        }

        if (userRepository.existsByEmail(email)) {
            println("Admin seed skipped: email '$email' already belongs to another user.")
            return
        }

        val city = try {
            City.valueOf(adminCity.trim().uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalStateException(
                "Invalid admin.seed.city '$adminCity'. Valid values are: ${City.entries.joinToString(", ")}"
            )
        }

        val admin = User(
            fullName = adminFullName.trim(),
            email = email,
            password = passwordEncoder.encode(rawPassword)!!,
            city = city,
            role = Role.ADMIN,
            isActive = false // Explicitly false so the admin must reset their password on first login
        )

        userRepository.save(admin)

        println("=================================================")
        println("Explicit ADMIN bootstrapping completed.")
        println("Admin Email: $email")
        println("=================================================")
    }
}