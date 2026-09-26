package org.example

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices

// 1. Класс с инструментами (Tools)
class MathTools {

    // Аннотация @Tool делает функцию доступной для LLM
    @Tool("Вычисляет факториал заданного числа. Используй этот инструмент для любых математических вычислений факториалов.")
    fun factorial(n: Int): Long {
        println("[Вызов инструмента]: Вычисляю факториал $n")
        return if (n <= 1) 1 else n * factorial(n - 1)
    }
}

// 2. Интерфейс нашего AI-агента
interface MathAgent {
    fun chat(userMessage: String): String
}

fun main() {
    // Получаем API ключ из переменных окружения (безопаснее, чем хардкодить)
    val apiKey = System.getenv("OPENAI_API_KEY")

    // 3. Инициализируем LLM (например, gpt-4o-mini)
    val model = OpenAiChatModel.builder()
        .baseUrl("https://api.minimax.io/v1")
        .apiKey(apiKey)
        .modelName("MiniMax-M3")
        .build()

    // 4. Собираем агента и "скармливаем" ему наши инструменты
    val agent = AiServices.builder(MathAgent::class.java)
        .chatLanguageModel(model)
        .tools(MathTools()) // <-- Подключаем инструменты
        .build()

    // 5. Запускаем диалог
    println("Отправляем запрос агенту...")
    val response = agent.chat("Привет! Посчитай, пожалуйста, факториал числа 6.")

    println("\nОтвет агента: $response")
}