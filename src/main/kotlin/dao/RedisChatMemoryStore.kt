package org.example.dao

import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.ChatMessageDeserializer
import dev.langchain4j.data.message.ChatMessageSerializer
import dev.langchain4j.store.memory.chat.ChatMemoryStore
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisChatMemoryStore(
    private val redis: StringRedisTemplate
) : ChatMemoryStore {

    private val log = LoggerFactory.getLogger(javaClass)
    private val ttl: Duration = Duration.ofHours(24)

    private fun key(memoryId: Any): String = "chat:$memoryId"

    override fun getMessages(memoryId: Any): MutableList<ChatMessage> {
        val json = redis.opsForValue().get(key(memoryId)) ?: return mutableListOf()
        return ChatMessageDeserializer.messagesFromJson(json).toMutableList()
    }

    override fun updateMessages(memoryId: Any, messages: MutableList<ChatMessage>) {
        val json = ChatMessageSerializer.messagesToJson(messages)
        redis.opsForValue().set(key(memoryId), json, ttl)
        log.debug("Persisted {} messages for memoryId={}", messages.size, memoryId)
    }

    override fun deleteMessages(memoryId: Any) {
        redis.delete(key(memoryId))
        log.debug("Deleted chat memory for memoryId={}", memoryId)
    }
}
