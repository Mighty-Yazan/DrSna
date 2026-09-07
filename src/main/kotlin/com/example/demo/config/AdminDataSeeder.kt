package com.example.demo.config

import com.example.demo.model.City
import com.example.demo.model.Role
import com.example.demo.model.User
import com.example.demo.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class AdminDataSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,

    @Value("\${admin.seed.email}")
    private val adminEmail: String,

    @Value("\${admin.seed.password}")
    private val adminPassword: String,

    @Value("\${admin.seed.full-name}")
    private val adminFullName: String,

    @Value("\${admin.seed.city}")
    private val adminCity: String
) : CommandLineRunner {

    override fun run(vararg args: String) {

        // Do not create another admin if one already exists.
        if (userRepository.existsByRole(Role.ADMIN)) {
            return
        }

        val email = adminEmail.trim().lowercase()

        // Extra protection in case the configured email belongs to another account.
        if (userRepository.existsByEmail(email)) {
            println(
                "Admin seed skipped: email '$email' already belongs to another user."
            )
            return
        }

        val city = try {
            City.valueOf(adminCity.trim().uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalStateException(
                "Invalid admin.seed.city '$adminCity'. " +
                        "Valid values are: ${City.entries.joinToString(", ")}"
            )
        }

        val admin = User(
            fullName = adminFullName.trim(),
            email = email,
            password = passwordEncoder.encode(adminPassword)!!,
            city = city,
            role = Role.ADMIN
        )

        userRepository.save(admin)

        println("=================================================")
        println("Initial ADMIN account created successfully.")
        println("Email: $email")
        println("=================================================")
    }
}