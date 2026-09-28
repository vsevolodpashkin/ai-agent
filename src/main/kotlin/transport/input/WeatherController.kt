package org.example.transport.input

import jakarta.validation.Valid
import org.example.business.agent.StandardAgent
import org.example.dto.WeatherRequest
import org.example.dto.WeatherResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/weather")
class WeatherController(private val agent: StandardAgent) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun query(@Valid @RequestBody request: WeatherRequest): WeatherResponse {
        val sessionId = request.sessionId ?: "default"
        val prompt = "Какая сейчас погода в городе ${request.location}? Дай прогноз на ${request.hours} часов."
        log.info("Weather query: location='{}', hours={}, sessionId={}", request.location, request.hours, sessionId)

        val answer = agent.chat(sessionId, prompt)
        val response = WeatherResponse(
            location = request.location,
            hours = request.hours,
            answer = answer,
            sessionId = sessionId,
            fetchedAt = Instant.now(),
        )
        log.info("Weather response sent ({} chars) for sessionId={}", answer.length, sessionId)
        return response
    }

    @PostMapping(path = ["/session"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun newSession(): Map<String, String> = mapOf("sessionId" to UUID.randomUUID().toString())
}
