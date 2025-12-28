package com.example.deepvoicechat.models

import kotlinx.serialization.Serializable

@Serializable
data class Model(
    val id: String,
    val name: String = id // fallback if only ID provided
)

@Serializable
data class Message(
    val role: String, // "user" or "assistant"
    val content: String
)

@Serializable
data class Reasoning(
    val effort: String // "none", "low", "medium", "high"
)

@Serializable
data class ChatRequest(
    val provider: String,
    val model: String,
    val messages: List<Message>,
    val stream: Boolean = false,
    val max_tokens: Int? = null,
    val max_completion_tokens: Int? = null,
    val reasoning: Reasoning? = null,
    val temperature: Double? = null
)

@Serializable
data class ChatResponse(
    val content: String // Assumed format from proxy
)

object Providers {
    val list = listOf("OpenAI", "Anthropic", "Gemini")
}
