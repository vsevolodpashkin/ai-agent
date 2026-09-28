package org.example.business.agent

import dev.langchain4j.memory.chat.ChatMemoryProvider
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.mcp.McpToolProvider
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.tool.ToolProvider
import org.example.business.mcp.CompositeToolProvider
import org.example.business.mcp.StaticToolProvider
import org.example.business.tools.MathTools
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AgentConfig {

    @Bean
    fun mathAgent(
        chatLanguageModel: ChatLanguageModel,
        chatMemoryProvider: ChatMemoryProvider,
        mathTools: MathTools,
        @Qualifier("compositeToolProvider") toolProvider: ToolProvider
    ): MathAgent = AiServices.builder(MathAgent::class.java)
        .chatLanguageModel(chatLanguageModel)
        .chatMemoryProvider(chatMemoryProvider)
        .toolProvider(toolProvider)
        .build()

    @Bean
    fun compositeToolProvider(
        mathTools: MathTools,
        mcpToolProvider: McpToolProvider
    ): ToolProvider = CompositeToolProvider(
        listOf(
            StaticToolProvider(listOf(mathTools)),
            mcpToolProvider
        )
    )
}
