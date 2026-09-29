package com.example.demo.security

import com.example.demo.repository.RefreshTokenRepository
import com.example.demo.repository.UserRepository
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.util.UUID

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val rsaKeyProperties: RsaKeyProperties,
    private val tokenBlacklistService: TokenBlacklistService,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity
    ): SecurityFilterChain {

        http
            /*
             * The application uses stateless Bearer access tokens.
             *
             * The refresh token is transported through an HttpOnly cookie.
             */
            .csrf { it.disable() }

            .cors { }

            .sessionManagement {
                it.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            }

            .authorizeHttpRequests { auth ->

                auth
                    .requestMatchers(
                        "/api/health",

                        "/api/auth/register/user",
                        "/api/auth/register/clinic",
                        "/api/auth/login",
                        "/api/auth/refresh"
                    )
                    .permitAll()

                    /*
                     * IMPORTANT:
                     *
                     * Appointment booking must NOT be public.
                     *
                     * A patient must have a valid authenticated
                     * access token to create a booking.
                     */
                    .anyRequest()
                    .authenticated()
            }

            .oauth2ResourceServer { oauth2 ->

                oauth2.jwt { jwt ->

                    jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()
                    )
                }
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource():
            CorsConfigurationSource {

        val configuration =
            CorsConfiguration()

        configuration.allowedOrigins =
            listOf(
                "http://localhost:5173"
            )

        configuration.allowedMethods =
            listOf(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
            )

        configuration.allowedHeaders =
            listOf("*")

        /*
         * Required because the refresh token is stored
         * in an HttpOnly cookie.
         */
        configuration.allowCredentials = true

        val source =
            UrlBasedCorsConfigurationSource()

        source.registerCorsConfiguration(
            "/**",
            configuration
        )

        return source
    }

    @Bean
    fun jwtAuthenticationConverter():
            JwtAuthenticationConverter {

        val authoritiesConverter =
            org.springframework.security.oauth2.server.resource.authentication
                .JwtGrantedAuthoritiesConverter()
                .apply {

                    /*
                     * TokenService creates:
                     *
                     * roles = ["ROLE_PATIENT"]
                     * roles = ["ROLE_DOCTOR"]
                     * roles = ["ROLE_CLINIC"]
                     * roles = ["ROLE_ADMIN"]
                     */
                    setAuthoritiesClaimName(
                        "roles"
                    )

                    /*
                     * The ROLE_ prefix already exists
                     * inside the JWT values.
                     *
                     * Therefore do NOT add another ROLE_ prefix.
                     */
                    setAuthorityPrefix("")
                }

        return JwtAuthenticationConverter().apply {

            setJwtGrantedAuthoritiesConverter(
                authoritiesConverter
            )
        }
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {

        /*
         * First perform normal cryptographic JWT validation.
         */
        val baseDecoder =
            NimbusJwtDecoder
                .withPublicKey(
                    rsaKeyProperties.publicKey
                )
                .build()

        /*
         * Then perform our application-level session validation.
         *
         * JWT signature validity alone is NOT enough.
         */
        return JwtDecoder { token ->

            val jwt =
                baseDecoder.decode(token)

            // =============================================================
            // 1. JTI BLACKLIST
            // =============================================================

            val jti =
                jwt.id

            if (
                jti != null &&
                tokenBlacklistService.isBlacklisted(jti)
            ) {
                throw JwtException(
                    "Token has been revoked"
                )
            }

            // =============================================================
            // 2. USER ID
            // =============================================================

            val userIdValue =
                jwt.getClaimAsString(
                    "userId"
                )
                    ?: throw JwtException(
                        "Token is missing userId"
                    )

            val userId =
                try {

                    UUID.fromString(
                        userIdValue
                    )

                } catch (
                    _: IllegalArgumentException
                ) {

                    throw JwtException(
                        "Invalid userId claim"
                    )
                }

            // =============================================================
            // 3. SECURITY VERSION
            // =============================================================

            /*
             * Every access token contains the user's
             * securityVersion at issuance time.
             *
             * Example:
             *
             * JWT:
             *     securityVersion = 5
             *
             * DB:
             *     securityVersion = 6
             *
             * Result:
             *     JWT INVALID
             *
             * This is what immediately invalidates old
             * access tokens after password changes,
             * resets, deactivation, etc.
             */
            val tokenSecurityVersion =
                (jwt.claims["securityVersion"] as? Number)
                    ?.toLong()
                    ?: throw JwtException(
                        "Token is missing securityVersion"
                    )

            // =============================================================
            // 4. LOAD CURRENT USER
            // =============================================================

            val user =
                userRepository
                    .findById(userId)
                    .orElse(null)
                    ?: throw JwtException(
                        "User no longer exists"
                    )

            // =============================================================
            // 5. GLOBAL ACCOUNT SECURITY VALIDATION
            // =============================================================

            if (
                user.securityVersion !=
                tokenSecurityVersion
            ) {

                throw JwtException(
                    "Session has been revoked"
                )
            }

            // =============================================================
            // 6. ACCOUNT ACTIVE STATUS
            // =============================================================

            /*
             * Defense in depth:
             *
             * Even if some service accidentally changes isActive
             * without incrementing securityVersion, an inactive
             * non-admin account cannot continue using the token.
             *
             * ADMIN is the exception because the project supports
             * the mandatory first-login password-change flow.
             */
            if (
                !user.isActive &&
                user.role != com.example.demo.model.Role.ADMIN
            ) {

                throw JwtException(
                    "Account is inactive"
                )
            }

            // =============================================================
            // 7. SESSION ID
            // =============================================================

            val sessionIdValue =
                jwt.getClaimAsString(
                    "sessionId"
                )
                    ?: throw JwtException(
                        "Token is missing sessionId"
                    )

            val sessionId =
                try {

                    UUID.fromString(
                        sessionIdValue
                    )

                } catch (
                    _: IllegalArgumentException
                ) {

                    throw JwtException(
                        "Invalid sessionId claim"
                    )
                }

            // =============================================================
            // 8. SESSION VERSION
            // =============================================================

            /*
             * Refresh rotation changes tokenVersion:
             *
             * old refresh:
             *     tokenVersion = 0
             *
             * new refresh:
             *     tokenVersion = 1
             *
             * Therefore the Access Token created from the old
             * session becomes invalid immediately.
             */
            val sessionVersion =
                (jwt.claims["sessionVersion"] as? Number)
                    ?.toLong()
                    ?: throw JwtException(
                        "Token is missing sessionVersion"
                    )

            // =============================================================
            // 9. FIND CURRENT SESSION
            // =============================================================

            /*
             * A valid access token must still have a corresponding
             * refresh-session row in the database.
             *
             * If logout / password change / deactivation revoked
             * that session, this access token is no longer valid.
             */
            val currentSession =
                refreshTokenRepository
                    .findActiveBySessionId(sessionId)
                    ?: throw JwtException(
                        "Session has been revoked"
                    )

            // =============================================================
            // 10. FINAL SESSION VALIDATION
            // =============================================================

            if (
                currentSession.user.id != userId ||

                currentSession.securityVersion !=
                user.securityVersion ||

                currentSession.tokenVersion !=
                sessionVersion
            ) {

                throw JwtException(
                    "Session has been revoked"
                )
            }

            /*
             * All validations passed.
             *
             * Spring Security can now treat this JWT
             * as an authenticated principal.
             */
            jwt
        }
    }

    @Bean
    fun jwtEncoder(): JwtEncoder {

        /*
         * RS256 signing:
         *
         * private key -> signs the JWT
         * public key  -> validates the JWT
         */
        val jwk =
            RSAKey.Builder(
                rsaKeyProperties.publicKey
            )
                .privateKey(
                    rsaKeyProperties.privateKey
                )
                .build()

        val jwks:
                JWKSource<SecurityContext> =
            ImmutableJWKSet(
                JWKSet(jwk)
            )

        return NimbusJwtEncoder(
            jwks
        )
    }

    @Bean
    fun passwordEncoder():
            PasswordEncoder =
        BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(
        config: AuthenticationConfiguration
    ): AuthenticationManager =
        config.authenticationManager
}