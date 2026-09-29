package com.example.demo.exception

class RefreshTokenReuseException(
    message: String = "Refresh token reuse detected"
) : RuntimeException(message)