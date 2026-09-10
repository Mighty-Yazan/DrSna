package com.example.demo.security

import org.springframework.context.annotation.Configuration
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Configuration
class RsaKeyProperties {

    lateinit var publicKey: RSAPublicKey
    lateinit var privateKey: RSAPrivateKey

    init {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair: KeyPair = keyPairGenerator.generateKeyPair()
        this.publicKey = keyPair.public as RSAPublicKey
        this.privateKey = keyPair.private as RSAPrivateKey
    }
}
