package ru.itmo.aiex.persona.dto

data class StyleView(
    val avgMessageLength: Double,
    val emojiPerMessage: Double,
    val topEmojis: List<String>,
    val capsShare: Double,
    val lowercaseStartShare: Double,
    val replySpeed: String,
    val samplePhrases: List<String>,
)
