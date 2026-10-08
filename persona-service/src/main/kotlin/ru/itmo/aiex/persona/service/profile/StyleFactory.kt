package ru.itmo.aiex.persona.service.profile

import ru.itmo.aiex.persona.dto.CorpusSnapshot
import ru.itmo.aiex.persona.dto.StyleView
import java.math.BigDecimal
import java.math.RoundingMode

object StyleFactory {
    private const val SCALE = 3

    fun from(snapshot: CorpusSnapshot): StyleView {
        val stats = snapshot.stats
        return StyleView(
            avgMessageLength = round(stats.avgMessageLength),
            emojiPerMessage = round(stats.emojiPerMessage),
            topEmojis = stats.topEmojis,
            capsShare = round(stats.capsShare),
            lowercaseStartShare = round(stats.lowercaseStartShare),
            replySpeed = ReplySpeed.of(stats.avgReplyDelaySeconds).code,
            samplePhrases = snapshot.samplePhrases.map(PromptBuilder::sanitizePhrase).filter { it.isNotEmpty() },
        )
    }

    private fun round(value: Double): Double = if (value.isNaN()) 0.0 else BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).toDouble()
}
