package org.example.business.tools

import dev.langchain4j.agent.tool.Tool
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MathTools {

    private val log = LoggerFactory.getLogger(javaClass)

    @Tool("Вычисляет факториал заданного неотрицательного целого числа (n <= 20)")
    fun factorial(n: Int): Long {
        log.info("Tool call: factorial({})", n)
        require(n in 0..20) { "n must be in 0..20 to avoid Long overflow, got $n" }
        return if (n <= 1) 1L else n * factorial(n - 1)
    }
}
