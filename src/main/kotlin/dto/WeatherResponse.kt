package org.example.dto

import java.time.Instant

data class WeatherResponse(
    val location: String,
    val hours: Int,
    val answer: String,
    val sessionId: String,
    val fetchedAt: Instant,
)
