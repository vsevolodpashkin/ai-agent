package org.example

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ChatMessageDeserializer
import dev.langchain4j.data.message.ChatMessageSerializer
import dev.langchain4j.memory.chat.MessageWindowChatMemory
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import org.springframework.data.redis.connection.RedisPassword
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration


class MathTools {
    @Tool("Вычисляет факториал заданного числа")
    fun factorial(n: Int): Long {
        println("[Вызов инструмента]: Вычисляю факториал $n")
        return if (n <= 1) 1 else n * factorial(n - 1)
    }
}

class RedisChatMemoryStore(
    private val redis: StringRedisTemplate
) : ChatMemoryStore {

    private fun key(memoryId: Any): String = "chat:$memoryId"

    override fun getMessages(memoryId: Any): MutableList<ChatMessage> {
        val json = redis.opsForValue().get(key(memoryId)) ?: return mutableListOf()
        return ChatMessageDeserializer.messagesFromJson(json).toMutableList()
    }

    override fun updateMessages(memoryId: Any, messages: MutableList<ChatMessage>) {
        val json = ChatMessageSerializer.messagesToJson(messages)
        redis.opsForValue().set(key(memoryId), json, Duration.ofHours(24))
    }

    override fun deleteMessages(memoryId: Any) {
        redis.delete(key(memoryId))
    }
}

interface MathAgent {
    fun chat(userMessage: String): String
}

fun main() {
    val apiKey = System.getenv("OPENAI_API_KEY")
    val model = OpenAiChatModel.builder()
        .baseUrl("https://api.minimax.io/v1")
        .apiKey(apiKey)
        .modelName("MiniMax-M3")
        .build()

    val redisHost = System.getenv("REDIS_HOST") ?: "localhost"
    val redisPort = System.getenv("REDIS_PORT")?.toInt() ?: 6379
    val redisPassword = System.getenv("REDIS_PASSWORD")
    val memoryId = System.getenv("MEMORY_ID") ?: "default"

    val redisConfig = RedisStandaloneConfiguration(redisHost, redisPort).apply {
        if (!redisPassword.isNullOrBlank()) {
            setPassword(RedisPassword.of(redisPassword))
        }
    }
    val connectionFactory = LettuceConnectionFactory(redisConfig).apply {
        afterPropertiesSet()
    }
    val redisTemplate = StringRedisTemplate(connectionFactory).apply {
        afterPropertiesSet()
    }
    val memoryStore = RedisChatMemoryStore(redisTemplate)

    val chatMemory = MessageWindowChatMemory.builder()
        .id(memoryId)
        .maxMessages(10)
        .chatMemoryStore(memoryStore)
        .build()

    val agent = AiServices.builder(MathAgent::class.java)
        .chatLanguageModel(model)
        .chatMemory(chatMemory)
        .tools(MathTools())
        .build()

    println("Отправляем запрос агенту...")

    val response = agent.chat("Привет! Посчитай, пожалуйста, факториал числа 6.")
    println("\nОтвет агента: $response")

    println("Отправляем новый запрос агенту...")

    val response2 = agent.chat("Выведи результат прошлого вычисления")
    println("\nОтвет агента: $response2")
}

