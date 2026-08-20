package com.example.demo.exception

// Base class for application exceptions
open class AppException(message: String) : RuntimeException(message)

// Resource not found
class ResourceNotFoundException(message: String) : AppException(message)

// Duplicate resource (e.g. email or license already exists)
class DuplicateResourceException(message: String) : AppException(message)

// Invalid login credentials
class InvalidCredentialsException(message: String) : AppException(message)

// Password and confirm password do not match
class PasswordMismatchException(message: String = "Password and confirm password do not match") : AppException(message)