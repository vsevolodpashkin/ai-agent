package org.example.business.memory

import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import org.example.business.agent.AgentProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MemoryConfig {

    /**
     * Provides per-memoryId ChatMemory instances backed by the ChatMemoryStore bean
     * (RedisChatMemoryStore). The MathAgent @AiService consumes this provider and
     * routes each @MemoryId-annotated call to a fresh MessageWindowChatMemory.
     */
    @Bean
    fun chatMemoryProvider(
        store: ChatMemoryStore,
        properties: AgentProperties
    ): ChatMemoryProvider = ChatMemoryProvider { memoryId ->
        MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(properties.memory.maxMessages)
            .chatMemoryStore(store)
            .build()
    }
}
