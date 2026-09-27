package org.example.business.agent

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "agent")
data class AgentProperties(
    val memory: Memory = Memory()
) {
    data class Memory(
        val maxMessages: Int = 10,
        val defaultId: String = "default"
    )
}
