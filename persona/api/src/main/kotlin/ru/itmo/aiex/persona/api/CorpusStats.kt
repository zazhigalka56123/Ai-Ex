package ru.itmo.aiex.persona.api

data class CorpusStats(
    val avgMessageLength: Double,
    val medianMessageLength: Int,
    val emojiPerMessage: Double,
    val topEmojis: List<String>,
    val capsShare: Double,
    val lowercaseStartShare: Double,
    val questionShare: Double,
    val exclamationShare: Double,
    val ellipsisShare: Double,
    val nightShare: Double,
    val hourHistogram: List<Int>,
    val avgReplyDelaySeconds: Long?,
    val initiativeShare: Double,
    val topWords: List<String>,
    val jealousyMarkers: Int,
    val affectionMarkers: Int,
)
