package com.example.demo

import com.example.demo.config.AdminDataSeeder
import com.example.demo.model.Role
import com.example.demo.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.boot.test.util.TestPropertyValues
import java.io.File

/**
 * Loads the project's .env file into Spring's test Environment.
 *
 * The tests below reference variable NAMES such as ${ADMIN_SEED_EMAIL};
 * they never contain the real secret values.
 */
class DotEnvTestInitializer :
    ApplicationContextInitializer<ConfigurableApplicationContext> {

    override fun initialize(context: ConfigurableApplicationContext) {
        val envFile = File(".env")

        if (!envFile.isFile) {
            return
        }

        val properties = envFile.readLines()
            .asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val normalized = if (line.startsWith("export ")) {
                    line.removePrefix("export ").trim()
                } else {
                    line
                }

                val separator = normalized.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    val key = normalized.substring(0, separator).trim()
                    var value = normalized.substring(separator + 1).trim()

                    // Support simple quoted .env values.
                    if (value.length >= 2) {
                        val first = value.first()
                        val last = value.last()
                        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                            value = value.substring(1, value.length - 1)
                        }
                    }

                    key to value
                }
            }
            .toMap()

        TestPropertyValues.of(
            properties.map { (key, value) -> "$key=$value" }
        ).applyTo(context.environment)
    }
}

@SpringBootTest
@ContextConfiguration(initializers = [DotEnvTestInitializer::class])
@TestPropertySource(
    properties = [
        "admin.seed.enabled=false"
    ]
)
class AdminBootstrapDisabledTest {

    @jakarta.annotation.Resource
    lateinit var applicationContext: ApplicationContext

    @Test
    fun `admin seeder stays disabled unless explicitly enabled`() {
        assertTrue(
            applicationContext.getBeansOfType(AdminDataSeeder::class.java).isEmpty(),
            "AdminDataSeeder must not be created when admin.seed.enabled=false"
        )
    }
}

@SpringBootTest
@ContextConfiguration(initializers = [DotEnvTestInitializer::class])
@TestPropertySource(
    properties = [
        "admin.seed.enabled=true"
    ]
)
class AdminBootstrapEnabledTest {

    @jakarta.annotation.Resource
    lateinit var userRepository: UserRepository

    @jakarta.annotation.Resource
    lateinit var environment: Environment

    @Test
    fun `admin seeder uses admin values from environment properties`() {
        // These are the variable names from .env; the test never contains their real values.
        val adminEmail = environment.getRequiredProperty("ADMIN_SEED_EMAIL")

        val adminOptional = userRepository.findByEmail(adminEmail.trim().lowercase())

        assertTrue(
            adminOptional.isPresent,
            "Admin account should exist for ADMIN_SEED_EMAIL=$adminEmail when ADMIN_SEED_ENABLED=true"
        )

        val admin = adminOptional.get()

        assertEquals(Role.ADMIN, admin.role)
        assertFalse(
            admin.isActive,
            "Bootstrapped admin must start inactive so the first-login password reset flow can apply"
        )
        assertEquals(
            adminEmail.trim().lowercase(),
            admin.email,
            "The seeded admin email must come from ADMIN_SEED_EMAIL"
        )
    }


}
