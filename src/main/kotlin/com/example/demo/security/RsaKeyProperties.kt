package com.example.demo.security

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Configuration
class RsaKeyProperties(
    @Value("classpath:certs/app-public.pem")
    private val publicKeyResource: Resource,

    @Value("classpath:certs/app-private.pem")
    private val privateKeyResource: Resource
) {

    lateinit var publicKey: RSAPublicKey
    lateinit var privateKey: RSAPrivateKey

    @PostConstruct //meaning excute after spring injects the dependencies(priv,pub key)so they are not null when read
    fun loadKeys() {
        val keyFactory = KeyFactory.getInstance("RSA")

        // 1. Read and parse the Public Key
        val publicKeyPem = publicKeyResource.inputStream.bufferedReader().use { it.readText() }//close after reading
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val publicBytes = Base64.getDecoder().decode(publicKeyPem)
        this.publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicBytes)) as RSAPublicKey // Converts a  key specification into a usable Key object its not actually generating

        // 2. Read and parse the Private Key
        val privateKeyPem = privateKeyResource.inputStream.bufferedReader().use { it.readText() }
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val privateBytes = Base64.getDecoder().decode(privateKeyPem)
        this.privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateBytes)) as RSAPrivateKey
    }
}