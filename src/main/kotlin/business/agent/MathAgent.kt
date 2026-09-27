package org.example.business.agent

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.spring.AiService

/**
 * AI agent contract. The @AiService annotation triggers langchain4j-spring-boot-starter
 * to auto-create an AiServices proxy and register it as a Spring bean named "mathAgent".
 *
 * Wiring (AUTOMATIC mode):
 *   - ChatLanguageModel: OpenAiChatModel (from langchain4j-open-ai-spring-boot-starter)
 *   - ChatMemoryProvider: provided by MemoryConfig (per-memoryId Redis-backed memory)
 *   - Tools: any bean containing @Tool methods (e.g. MathTools)
 */
@AiService
interface MathAgent {
    fun chat(@MemoryId memoryId: String, @UserMessage userMessage: String): String
}
