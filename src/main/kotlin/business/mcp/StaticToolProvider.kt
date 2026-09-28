package org.example.business.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.agent.tool.ToolSpecifications
import dev.langchain4j.service.tool.ToolProvider
import dev.langchain4j.service.tool.ToolProviderRequest
import dev.langchain4j.service.tool.ToolProviderResult
import org.slf4j.LoggerFactory

/**
 * Exposes one or more objects with @Tool-annotated methods as a ToolProvider.
 * Equivalent to AiServices.builder().tools(obj) but usable inside a composite provider,
 * which is necessary when .tools() and .toolProvider() cannot coexist on the same builder.
 */
class StaticToolProvider(
    private val objectsWithTools: List<Any>
) : ToolProvider {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()

    private data class BoundTool(val specification: ToolSpecification, val invoke: (ToolExecutionRequest) -> String)

    private val boundTools: List<BoundTool> = objectsWithTools.flatMap { obj ->
        ToolSpecifications.toolSpecificationsFrom(obj).map { spec ->
            val method = obj.javaClass.declaredMethods.first { m ->
                val annotation = m.getAnnotation(dev.langchain4j.agent.tool.Tool::class.java)
                val declaredName = annotation?.name?.takeIf { it.isNotBlank() } ?: m.name
                declaredName == spec.name()
            }
            BoundTool(spec) { request -> invokeTool(obj, method, request) }
        }
    }

    override fun provideTools(request: ToolProviderRequest): ToolProviderResult {
        val builder = ToolProviderResult.builder()
        boundTools.forEach { bound ->
            builder.add(bound.specification) { executionRequest, _ -> bound.invoke(executionRequest) }
        }
        return builder.build()
    }

    private fun invokeTool(obj: Any, method: java.lang.reflect.Method, request: ToolExecutionRequest): String {
        val argsNode = objectMapper.readTree(request.arguments()) as ObjectNode
        val args = method.parameters.map { param ->
            val argNode = argsNode.get(param.name)
            objectMapper.convertValue(argNode, param.type)
        }.toTypedArray()
        return try {
            val result = method.invoke(obj, *args)
            result?.toString() ?: ""
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.cause ?: e
            log.error("Tool execution failed: {}", cause.message, cause)
            "Error: ${cause.message}"
        }
    }
}
