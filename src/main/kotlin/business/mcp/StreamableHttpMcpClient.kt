package org.example.business.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP client for the Streamable HTTP transport (MCP spec 2025-03-26+).
 * The official HttpMcpTransport in langchain4j-mcp 1.0.0-beta1 only supports the
 * legacy SSE-over-GET pattern; the ProjectEOL Weather server uses Streamable HTTP
 * (POST-only with JSON-RPC bodies), so we implement it directly here.
 *
 * Protocol details:
 *   - Every operation is a POST to the same URL with a JSON-RPC 2.0 body
 *   - Responses are plain JSON (server may also stream SSE, which we handle)
 *   - The initialize handshake sends an `initialize` request followed by a
 *     `notifications/initialized` notification
 */
class StreamableHttpMcpClient(
    private val url: String,
    private val clientName: String,
    private val clientVersion: String,
    private val timeout: Duration
) : McpClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper()
    private val idGenerator = AtomicLong(0)
    private val jsonMediaType = "application/json".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(timeout.seconds, TimeUnit.SECONDS)
        .connectTimeout(timeout.seconds, TimeUnit.SECONDS)
        .readTimeout(timeout.seconds, TimeUnit.SECONDS)
        .writeTimeout(timeout.seconds, TimeUnit.SECONDS)
        .build()

    init {
        val initResponse = sendRequest(buildInitializeRequest())
        log.info("MCP server initialized: {}", initResponse.get("result"))
        sendNotification(buildInitializedNotification())
    }

    override fun listTools(): List<ToolSpecification> {
        val response = sendRequest(buildListToolsRequest(idGenerator.incrementAndGet()))
        val toolsArray = response.get("result").get("tools") as ArrayNode
        return toolsArray.map { parseToolSpecification(it) }
    }

    override fun executeTool(executionRequest: ToolExecutionRequest): String {
        val arguments = objectMapper.readTree(executionRequest.arguments())
        val response = sendRequest(
            buildCallToolRequest(
                idGenerator.incrementAndGet(),
                executionRequest.name(),
                arguments
            )
        )
        return extractResult(response)
    }

    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
    }

    private fun buildInitializeRequest(): JsonNode =
        JsonNodeFactory.instance.objectNode().apply {
            put("jsonrpc", "2.0")
            put("id", idGenerator.incrementAndGet())
            put("method", "initialize")
            val params = JsonNodeFactory.instance.objectNode().apply {
                put("protocolVersion", "2024-11-05")
                val clientInfo = JsonNodeFactory.instance.objectNode().apply {
                    put("name", clientName)
                    put("version", clientVersion)
                }
                set<JsonNode>("clientInfo", clientInfo)
                val capabilities = JsonNodeFactory.instance.objectNode().apply {
                    val roots = JsonNodeFactory.instance.objectNode().apply {
                        put("listChanged", false)
                    }
                    set<JsonNode>("roots", roots)
                }
                set<JsonNode>("capabilities", capabilities)
            }
            set<JsonNode>("params", params)
        }

    private fun buildInitializedNotification(): JsonNode =
        JsonNodeFactory.instance.objectNode().apply {
            put("jsonrpc", "2.0")
            put("method", "notifications/initialized")
        }

    private fun buildListToolsRequest(id: Long): JsonNode =
        JsonNodeFactory.instance.objectNode().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/list")
            set<JsonNode>("params", JsonNodeFactory.instance.objectNode())
        }

    private fun buildCallToolRequest(id: Long, name: String, arguments: JsonNode): JsonNode =
        JsonNodeFactory.instance.objectNode().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", "tools/call")
            val params = JsonNodeFactory.instance.objectNode().apply {
                put("name", name)
                set<JsonNode>("arguments", arguments)
            }
            set<JsonNode>("params", params)
        }

    private fun sendRequest(payload: JsonNode): JsonNode {
        val body = objectMapper.writeValueAsString(payload)
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw RuntimeException("MCP request failed: HTTP ${response.code} $errorBody")
            }
            return parseResponseBody(response.body?.string().orEmpty())
        }
    }

    private fun sendNotification(payload: JsonNode) {
        val body = objectMapper.writeValueAsString(payload)
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                log.warn("Notification delivery failed: {}", e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun parseResponseBody(body: String): JsonNode {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) {
            throw RuntimeException("Empty response from MCP server")
        }
        if (trimmed.startsWith("{")) {
            return objectMapper.readTree(trimmed)
        }
        // SSE: collect all `data:` lines and concatenate their JSON payloads
        val dataLines = trimmed.lines().filter { it.startsWith("data:") }
        require(dataLines.isNotEmpty()) { "Unrecognized MCP response format: $trimmed" }
        val merged = dataLines.joinToString("") { it.removePrefix("data:").trim() }
        return objectMapper.readTree(merged)
    }

    private fun parseToolSpecification(node: JsonNode): ToolSpecification {
        val builder = ToolSpecification.builder()
        builder.name(node.get("name").asText())
        if (node.has("description")) {
            builder.description(node.get("description").asText())
        }
        builder.parameters(parseObjectSchema(node.get("inputSchema")))
        return builder.build()
    }

    private fun parseObjectSchema(node: JsonNode): JsonObjectSchema {
        val builder = JsonObjectSchema.builder()
        if (node.has("description")) {
            builder.description(node.get("description").asText())
        }
        if (node.has("required")) {
            val required = (node.get("required") as ArrayNode)
                .map { it.asText() }
                .toTypedArray()
            builder.required(*required)
        }
        if (node.has("additionalProperties")) {
            builder.additionalProperties(node.get("additionalProperties").asBoolean(false))
        }
        if (node.has("properties")) {
            val properties = node.get("properties")
            properties.fields().forEach { (name, propNode) ->
                builder.addProperty(name, parseSchemaElement(propNode))
            }
        }
        return builder.build()
    }

    private fun parseSchemaElement(node: JsonNode): JsonSchemaElement {
        val type = node.get("type").asText()
        return when (type) {
            "string" -> {
                if (node.has("enum")) {
                    val values = (node.get("enum") as ArrayNode)
                        .map { it.asText() }
                        .toTypedArray()
                    JsonEnumSchema.builder().apply {
                        if (node.has("description")) description(node.get("description").asText())
                        enumValues(*values)
                    }.build()
                } else {
                    JsonStringSchema.builder().apply {
                        if (node.has("description")) description(node.get("description").asText())
                    }.build()
                }
            }
            "number" -> JsonNumberSchema.builder().apply {
                if (node.has("description")) description(node.get("description").asText())
            }.build()
            "integer" -> JsonIntegerSchema.builder().apply {
                if (node.has("description")) description(node.get("description").asText())
            }.build()
            "boolean" -> JsonBooleanSchema.builder().apply {
                if (node.has("description")) description(node.get("description").asText())
            }.build()
            "array" -> JsonArraySchema.builder().apply {
                if (node.has("description")) description(node.get("description").asText())
                items(parseSchemaElement(node.get("items")))
            }.build()
            "object" -> parseObjectSchema(node)
            else -> throw IllegalArgumentException("Unknown JSON schema type: $type")
        }
    }

    private fun extractResult(result: JsonNode): String {
        if (result.has("result")) {
            val resultNode = result.get("result")
            if (resultNode.has("content")) {
                val contents = resultNode.get("content") as ArrayNode
                return contents.joinToString("\n") { it.get("text").asText() }
            }
            log.warn("MCP result has no 'content' field: {}", resultNode)
            return ""
        }
        if (result.has("error")) {
            val error = result.get("error")
            val message = error.get("message")?.asText() ?: "unknown error"
            log.warn("MCP tool call failed: {} (code={})", message, error.get("code")?.asInt())
            return "Error: $message"
        }
        log.warn("MCP response contains neither 'result' nor 'error': {}", result)
        return ""
    }
}
