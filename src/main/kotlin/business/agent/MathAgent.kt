package org.example.business.agent

import dev.langchain4j.service.MemoryId
import dev.langchain4j.service.UserMessage

interface StandardAgent {
    fun chat(@MemoryId memoryId: String, @UserMessage userMessage: String): String
}
