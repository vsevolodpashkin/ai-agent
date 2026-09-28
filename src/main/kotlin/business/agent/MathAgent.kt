package org.example.business.agent

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.UserMessage

/**
 * AI agent contract. The bean is created manually in AgentConfig via AiServices.builder()
 * to support both in-process @Tool beans (e.g. MathTools) and dynamic MCP tools via
 * McpToolProvider. Using @AiService here would not work because its AUTOMATIC mode does
 * not wire ToolProvider beans — only @Tool-annotated methods.
 */
interface MathAgent {
    fun chat(@MemoryId memoryId: String, @UserMessage userMessage: String): String
}
