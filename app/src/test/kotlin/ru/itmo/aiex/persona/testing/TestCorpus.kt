package ru.itmo.aiex.persona.testing

import ru.itmo.aiex.persona.dto.CorpusSnapshot
import ru.itmo.aiex.persona.dto.CorpusStats
import java.time.Instant
import java.util.UUID

object TestCorpus {
    val COLD_PHRASES = listOf("неа", "ну ок", "ГДЕ ТЫ БЫЛ", "с кем?", "сплю уже", "ладно...")

    fun coldStats(): CorpusStats = CorpusStats(
        avgMessageLength = 12.4,
        medianMessageLength = 10,
        emojiPerMessage = 0.1,
        topEmojis = listOf("🙄"),
        capsShare = 0.3,
        lowercaseStartShare = 0.8,
        questionShare = 0.2,
        exclamationShare = 0.05,
        ellipsisShare = 0.1,
        nightShare = 0.4,
        hourHistogram = List(24) { if (it < 4) 5 else 1 },
        avgReplyDelaySeconds = 4_000,
        initiativeShare = 0.3,
        topWords = listOf("опять", "спать"),
        jealousyMarkers = 10,
        affectionMarkers = 0,
    )

    fun warmStats(): CorpusStats = coldStats().copy(
        avgMessageLength = 95.0,
        emojiPerMessage = 1.4,
        topEmojis = listOf("❤", "😘"),
        capsShare = 0.0,
        nightShare = 0.05,
        avgReplyDelaySeconds = 40,
        initiativeShare = 0.7,
        jealousyMarkers = 0,
        affectionMarkers = 20,
    )

    fun snapshot(
        stats: CorpusStats = coldStats(),
        phrases: List<String> = COLD_PHRASES,
        importId: UUID = UUID.randomUUID(),
        theirMessages: Int = 30,
    ) = CorpusSnapshot(
        importId = importId,
        source = "TELEGRAM_JSON",
        theirName = "Маша",
        totalMessages = theirMessages * 2,
        theirMessages = theirMessages,
        myMessages = theirMessages,
        attachments = 3,
        periodStart = Instant.parse("2024-03-01T20:00:00Z"),
        periodEnd = Instant.parse("2024-03-04T20:00:00Z"),
        stats = stats,
        samplePhrases = phrases,
    )
}
