package org.example.business.mcp

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.mcp.client.McpClient
import dev.langchain4j.model.chat.request.json.JsonArraySchema
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema
import dev.langchain4j.model.chat.request.json.JsonEnumSchema
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema
import dev.langchain4j.model.chat.request.json.JsonNumberSchema
import dev.langchain4j.model.chat.request.json.JsonObjectSchema
import dev.langchain4j.model.chat.request.json.JsonSchemaElement
import dev.langchain4j.model.chat.request.json.JsonStringSchema
import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool as SdkTool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Adapts the official Kotlin MCP SDK (io.modelcontextprotocol.kotlin.sdk) to langchain4j's
 * McpClient interface. The actual MCP protocol implementation (SSE, sessions, reconnection,
 * spec compliance) is delegated to the SDK; this class only bridges the two type systems:
 *
 *   SDK types:    kotlinx.serialization JsonElement, ToolSchema, TextContent, CallToolResult
 *   l4j types:    Jackson JsonNode, JsonObjectSchema, ToolSpecification, String result
 *
 * The langchain4j McpClient interface is synchronous; the SDK is suspend-based, so each call
 * is bridged via runBlocking. Acceptable for our CLI use case (CommandLineRunner). For a
 * future HTTP server, prefer the SDK's suspend API directly with WebFlux or a coroutine
 * controller.
 */
class KotlinSdkMcpClientAdapter(
    httpClient: HttpClient,
    url: String,
    clientName: String,
    clientVersion: String,
) : McpClient {

    private val objectMapper = ObjectMapper()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    private val sdkClient: Client = Client(
        clientInfo = Implementation(name = clientName, version = clientVersion)
    )
    private val transport: StreamableHttpClientTransport = StreamableHttpClientTransport(
        client = httpClient,
        url = url,
    )

    init {
        runBlocking { sdkClient.connect(transport) }
    }

    override fun listTools(): List<ToolSpecification> = runBlocking {
        sdkClient.listTools().tools.orEmpty().map(::mapTool)
    }

    override fun executeTool(executionRequest: ToolExecutionRequest): String = runBlocking {
        val arguments: Map<String, Any?> = if (executionRequest.arguments().isBlank()) {
            emptyMap()
        } else {
            objectMapper.readValue(executionRequest.arguments(), mapType)
        }
        val result = sdkClient.callTool(name = executionRequest.name(), arguments = arguments)
        renderResult(result)
    }

    override fun close() {
        runBlocking { sdkClient.close() }
    }

    private fun mapTool(tool: SdkTool): ToolSpecification {
        val builder = ToolSpecification.builder()
        builder.name(tool.name)
        tool.description?.let(builder::description)
        tool.inputSchema.properties?.let { props ->
            builder.parameters(mapParametersSchema(props, tool.inputSchema.required.orEmpty()))
        }
        return builder.build()
    }

    private fun mapParametersSchema(properties: JsonObject, required: List<String>): JsonObjectSchema {
        val builder = JsonObjectSchema.builder().required(*required.toTypedArray())
        properties.forEach { (name, schema) ->
            builder.addProperty(name, mapSchemaElement(schema))
        }
        return builder.build()
    }

    private fun mapSchemaElement(element: JsonElement): JsonSchemaElement {
        if (element !is JsonObject) {
            throw IllegalArgumentException("Schema property must be a JsonObject, got: $element")
        }
        val type = element["type"]?.jsonPrimitive?.contentOrNull ?: "object"
        return when (type) {
            "object" -> mapObjectSchema(element)
            "string" -> mapStringSchema(element)
            "number" -> JsonNumberSchema.builder().apply { describeFrom(element) }.build()
            "integer" -> JsonIntegerSchema.builder().apply { describeFrom(element) }.build()
            "boolean" -> JsonBooleanSchema.builder().apply { describeFrom(element) }.build()
            "array" -> JsonArraySchema.builder().apply {
                describeFrom(element)
                items(mapSchemaElement(element["items"] ?: JsonNull))
            }.build()
            else -> throw IllegalArgumentException("Unknown JSON schema type: $type")
        }
    }

    private fun mapObjectSchema(obj: JsonObject): JsonObjectSchema {
        val builder = JsonObjectSchema.builder()
        builder.describeFrom(obj)
        obj["required"]?.let { req ->
            val required = (req as JsonArray).map { it.jsonPrimitive.content }.toTypedArray()
            builder.required(*required)
        }
        obj["properties"]?.let { props ->
            val propsObj = props as JsonObject
            propsObj.forEach { (name, schema) ->
                builder.addProperty(name, mapSchemaElement(schema))
            }
        }
        return builder.build()
    }

    private fun mapStringSchema(obj: JsonObject): JsonSchemaElement {
        val enumValues = obj["enum"] as? JsonArray
        return if (enumValues != null) {
            val values = enumValues.map { it.jsonPrimitive.content }.toTypedArray()
            JsonEnumSchema.builder().apply {
                describeFrom(obj)
                enumValues(*values)
            }.build()
        } else {
            JsonStringSchema.builder().apply { describeFrom(obj) }.build()
        }
    }

    private fun JsonObjectSchema.Builder.describeFrom(obj: JsonObject) {
        obj["description"]?.let { description(it.jsonPrimitive.content) }
    }

    private fun JsonNumberSchema.Builder.describeFrom(obj: JsonObject) {
        obj["description"]?.let { description(it.jsonPrimitive.content) }
    }

    private fun JsonIntegerSchema.Builder.describeFrom(obj: JsonObject) {
        obj["description"]?.let { description(it.jsonPrimitive.content) }
    }

    private fun JsonBooleanSchema.Builder.describeFrom(obj: JsonObject) {
        obj["description"]?.let { description(it.jsonPrimitive.content) }
    }

    private fun JsonStringSchema.Builder.describeFrom(obj: JsonObject) {
        obj["description"]?.let { description(it.jsonPrimitive.content) }
    }

    private fun JsonEnumSchema.Builder.describeFrom(obj: JsonObject) {
        obj["description"]?.let { description(it.jsonPrimitive.content) }
    }

    private fun JsonArraySchema.Builder.describeFrom(obj: JsonObject) {
        obj["description"]?.let { description(it.jsonPrimitive.content) }
    }

    private fun renderResult(result: CallToolResult): String {
        val prefix = if (result.isError == true) "Error: " else ""
        return prefix + result.content.joinToString("\n") { renderContent(it) }
    }

    private fun renderContent(block: ContentBlock): String = when (block) {
        is TextContent -> block.text
        is ImageContent -> "[image: ${block.mimeType}]"
        is AudioContent -> "[audio: ${block.mimeType}]"
        is ResourceLink -> "[resource_link: ${block.uri}]"
        is EmbeddedResource -> "[embedded_resource: ${block.resource.uri}]"
    }
}
