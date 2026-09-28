package org.example.business.mcp

import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.mcp.client.McpClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class McpConfig {

    /**
     * ProjectEOL Weather MCP client. Connects to the server on construction and
     * completes the initialize handshake synchronously. If the server is unreachable,
     * the bean fails to create and the application context refuses to start (fail-fast).
     */
    @Bean(destroyMethod = "close")
    fun weatherMcpClient(properties: McpProperties): McpClient =
        StreamableHttpMcpClient(
            url = properties.url,
            clientName = properties.clientName,
            clientVersion = properties.clientVersion,
            timeout = properties.timeout
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