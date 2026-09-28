package org.example.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class WeatherRequest(
    @field:NotBlank(message = "location must not be blank")
    @field:Size(max = 200, message = "location must be at most 200 characters")
    val location: String,

    @field:Min(value = 1, message = "hours must be between 1 and 168")
    @field:Max(value = 168, message = "hours must be between 1 and 168")
    val hours: Int = 24,

    @field:Size(max = 100, message = "sessionId must be at most 100 characters")
    val sessionId: String? = null,
)
