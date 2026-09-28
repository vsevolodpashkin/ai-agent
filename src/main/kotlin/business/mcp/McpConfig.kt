package org.example.business.mcp

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.McpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpConfig {

    /**
     * Ktor HTTP client used by the Kotlin MCP SDK's StreamableHttpClientTransport.
     * CIO engine is purely Kotlin-coroutine-based, no extra runtime dependency beyond
     * what kotlin-sdk-client already pulls in. SSE plugin is required for the
     * SDK's stream parsing.
     */
    @Bean(destroyMethod = "close")
    fun mcpHttpClient(properties: McpProperties): HttpClient = HttpClient(CIO) {
        install(SSE)
        engine {
            requestTimeout = properties.timeout.toMillis()
        }
    }

    /**
     * ProjectEOL Weather MCP client. Delegates the actual MCP protocol to the official
     * Kotlin MCP SDK (Client + StreamableHttpClientTransport); this adapter only bridges
     * the SDK's type system to langchain4j's McpClient interface.
     */
    @Bean(destroyMethod = "close")
    fun weatherMcpClient(
        httpClient: HttpClient,
        properties: McpProperties,
    ): McpClient = KotlinSdkMcpClientAdapter(
        httpClient = httpClient,
        url = properties.url,
        clientName = properties.clientName,
        clientVersion = properties.clientVersion,
    )

    /**
     * Aggregates all McpClient beans into a single ToolProvider that the agent can
     * consume. failIfOneServerFails=false so a transient outage of one MCP server does
     * not block the agent — the failed server's tools are simply absent until it
     * recovers.
     */
    @Bean
    fun mcpToolProvider(mcpClients: List<McpClient>): McpToolProvider =
        McpToolProvider.builder()
            .mcpClients(mcpClients)
            .failIfOneServerFails(false)
            .build()
}
