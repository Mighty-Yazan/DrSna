package com.example.demo.exception

class ClinicNotOperationalException(
    message: String = "Your clinic is not approved or inactive. Operational actions are restricted."
) : RuntimeException(message)
