package org.example.business.mcp

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Configuration for the ProjectEOL Weather MCP server
 * (https://weatherapi.projecteol.ru/mcp/). Exposes three tools to the agent:
 *   - search_locations
 *   - get_forecast_metadata
 *   - get_weather_forecast
 */
@ConfigurationProperties(prefix = "mcp.weather")
data class McpProperties(
    val url: String,
    val timeout: Duration = Duration.ofSeconds(60),
    val logRequests: Boolean = false,
    val logResponses: Boolean = false,
    val clientName: String = "ai-agent",
    val clientVersion: String = "1.0.0"
)
