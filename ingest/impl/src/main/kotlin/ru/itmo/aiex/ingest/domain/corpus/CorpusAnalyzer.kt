package ru.itmo.aiex.ingest.domain.corpus

import ru.itmo.aiex.ingest.domain.MessageAuthor
import ru.itmo.aiex.persona.api.CorpusSnapshot
import ru.itmo.aiex.persona.api.CorpusStats
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

class CorpusAnalyzer {
    fun analyze(input: CorpusInput): CorpusSnapshot {
        val them = input.messages.filter { it.author == MessageAuthor.THEM }
        val sentTimes = input.messages.mapNotNull { it.sentAt }
        return CorpusSnapshot(
            importId = input.importId,
            source = input.source.name,
            theirName = input.theirName,
            totalMessages = input.messages.size,
            theirMessages = them.size,
            myMessages = input.messages.size - them.size,
            attachments = input.attachments,
            periodStart = sentTimes.minOrNull(),
            periodEnd = sentTimes.maxOrNull(),
            stats = stats(input.messages, them),
            samplePhrases = samplePhrases(them),
        )
    }

    private fun stats(all: List<CorpusMessage>, them: List<CorpusMessage>): CorpusStats {
        val bodies = them.map { it.body }
        val lengths = bodies.map { it.codePointCount(0, it.length) }.sorted()
        val emojis = bodies.flatMap(Emoji::extract)
        val hours = them.mapNotNull { it.localHour }
        return CorpusStats(
            avgMessageLength = round(lengths.average()),
            medianMessageLength = median(lengths),
            emojiPerMessage = round(ratio(emojis.size, bodies.size)),
            topEmojis = top(emojis, TOP_EMOJIS),
            capsShare = share(bodies, ::isCaps),
            lowercaseStartShare = share(bodies, ::startsLowercase),
            questionShare = share(bodies) { '?' in it },
            exclamationShare = share(bodies) { '!' in it },
            ellipsisShare = share(bodies) { "..." in it || '…' in it },
            nightShare = round(ratio(hours.count { it in NIGHT_HOURS }, hours.size)),
            hourHistogram = List(HOURS_PER_DAY) { hour -> hours.count { it == hour } },
            avgReplyDelaySeconds = avgReplyDelay(all),
            initiativeShare = initiativeShare(all),
            topWords = top(bodies.flatMap(::words), TOP_WORDS),
            jealousyMarkers = Markers.countMessages(bodies, Markers.JEALOUSY),
            affectionMarkers = Markers.countMessages(bodies, Markers.AFFECTION),
        )
    }

    private fun avgReplyDelay(messages: List<CorpusMessage>): Long? {
        val delays =
            messages.zipWithNext().mapNotNull { (previous, current) ->
                val from = previous.sentAt
                val to = current.sentAt
                val isReply = previous.author == MessageAuthor.ME && current.author == MessageAuthor.THEM
                if (isReply && from != null && to != null) {
                    Duration.between(from, to).seconds.takeIf { it in 0..MAX_REPLY_DELAY.seconds }
                } else {
                    null
                }
            }
        return if (delays.isEmpty()) null else Math.round(delays.average())
    }

    private fun initiativeShare(messages: List<CorpusMessage>): Double {
        var starts = 0
        var theirStarts = 0
        var previous: Instant? = null
        messages.forEachIndexed { index, message ->
            val sentAt = message.sentAt
            val gap = if (previous != null && sentAt != null) Duration.between(previous, sentAt) else null
            if (index == 0 || (gap != null && gap > CONVERSATION_GAP)) {
                starts++
                if (message.author == MessageAuthor.THEM) theirStarts++
            }
            if (sentAt != null) previous = sentAt
        }
        return round(ratio(theirStarts, starts))
    }

    private fun samplePhrases(them: List<CorpusMessage>): List<String> {
        val candidates =
            them
                .map { it.body.replace(WHITESPACE, " ").trim() }
                .filter { it.length in PHRASE_LENGTH && PrivacyFilter.isSafe(it) }
                .distinctBy { it.lowercase() }
        if (candidates.size <= SAMPLE_PHRASES) return candidates
        return List(SAMPLE_PHRASES) { candidates[it * candidates.size / SAMPLE_PHRASES] }
    }

    private fun isCaps(body: String): Boolean {
        val letters = body.codePoints().filter(Character::isLetter).toArray()
        return letters.size >= CAPS_MIN_LETTERS && letters.all(Character::isUpperCase)
    }

    private fun startsLowercase(body: String): Boolean {
        val first = body.codePoints().filter(Character::isLetter).findFirst()
        return first.isPresent && Character.isLowerCase(first.asInt)
    }

    private fun words(body: String): List<String> =
        body.lowercase().split(NON_LETTERS).map { it.trim('-') }.filter { it.length >= MIN_WORD_LENGTH && it !in StopWords.RU }

    private fun share(bodies: List<String>, predicate: (String) -> Boolean): Double = round(ratio(bodies.count(predicate), bodies.size))

    private fun ratio(part: Int, total: Int): Double = if (total == 0) 0.0 else part.toDouble() / total

    private fun median(sorted: List<Int>): Int = if (sorted.isEmpty()) 0 else (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2

    private fun top(values: List<String>, limit: Int): List<String> = values
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }

    private fun round(value: Double): Double = if (value.isNaN()) 0.0 else BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).toDouble()

    companion object {
        const val SAMPLE_PHRASES = 12
        const val TOP_EMOJIS = 5
        const val TOP_WORDS = 10
        private const val HOURS_PER_DAY = 24
        private const val CAPS_MIN_LETTERS = 3
        private const val MIN_WORD_LENGTH = 4
        private const val SCALE = 4
        private val NIGHT_HOURS = 0..5
        private val PHRASE_LENGTH = 2..120
        private val MAX_REPLY_DELAY: Duration = Duration.ofHours(24)
        private val CONVERSATION_GAP: Duration = Duration.ofHours(6)
        private val WHITESPACE = Regex("\\s+")
        private val NON_LETTERS = Regex("[^\\p{L}-]+")
    }
}
