package org.example.business.agent

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

/**
 * Demo entry point. In a production deployment this would be replaced by a REST adapter
 * (transport/input) or a message consumer; for now we exercise the agent inline.
 */
@Component
class AgentRunner(
    private val agent: MathAgent,
    private val properties: AgentProperties
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: Array<String>) {
        val memoryId = properties.memory.defaultId

        log.info("Sending first request to agent (memoryId={})", memoryId)
        val response = agent.chat(memoryId, "Какая погода сейчас в Москве?")
        log.info("Agent response: {}", response)

        log.info("Sending follow-up request to test Redis-backed memory")
        val response2 = agent.chat(memoryId, "Выведи результат прошлого запроса")
        log.info("Agent response: {}", response2)
    }
}
