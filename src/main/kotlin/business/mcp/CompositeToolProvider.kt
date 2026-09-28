package org.example.business.mcp

import dev.langchain4j.service.tool.ToolProvider
import dev.langchain4j.service.tool.ToolProviderRequest
import dev.langchain4j.service.tool.ToolProviderResult

/**
 * Aggregates multiple ToolProvider instances into one. Required because AiServices.builder()
 * permits either .tools() OR .toolProvider() but not both — to expose both in-process @Tool
 * beans (via a wrapping provider) and dynamic MCP tools, the providers must be merged.
 */
class CompositeToolProvider(
    private val providers: List<ToolProvider>
) : ToolProvider {

    override fun provideTools(request: ToolProviderRequest): ToolProviderResult {
        val builder = ToolProviderResult.builder()
        for (provider in providers) {
            provider.provideTools(request).tools().forEach { (spec, executor) ->
                builder.add(spec, executor)
            }
        }
        return builder.build()
    }
}
