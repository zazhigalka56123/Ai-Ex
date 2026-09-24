package ru.itmo.aiex.persona.domain.profile

import ru.itmo.aiex.persona.api.CorpusStats

class TraitExtractor {
    fun extract(stats: CorpusStats, theirMessages: Int): List<DerivedTrait> = buildList {
        replySpeed(stats)?.let(::add)
        add(messageLength(stats))
        add(emojiUsage(stats))
        add(share(CAPS, stats.capsShare, if (stats.capsShare > CAPS_OFTEN) "often" else "rare"))
        add(share(LOWERCASE_START, stats.lowercaseStartShare, if (stats.lowercaseStartShare > HALF) "yes" else "no"))
        add(share(NIGHT_OWL, stats.nightShare, if (stats.nightShare > NIGHT_OWL_SHARE) "yes" else "no"))
        add(markerTrait(JEALOUSY, markerScore(stats.jealousyMarkers, theirMessages)))
        add(markerTrait(AFFECTION, markerScore(stats.affectionMarkers, theirMessages)))
        add(initiative(stats))
        add(questions(stats))
    }

    private fun replySpeed(stats: CorpusStats): DerivedTrait? {
        val delay = stats.avgReplyDelaySeconds ?: return null
        val speed = ReplySpeed.of(delay)

        return DerivedTrait(REPLY_SPEED, speed.code, (delay.toDouble() / SLOW_SATURATION_SECONDS).clamp01())
    }

    private fun messageLength(stats: CorpusStats): DerivedTrait {
        val avg = stats.avgMessageLength
        val value =
            when {
                avg < SHORT_BELOW_CHARS -> "short"
                avg > LONG_ABOVE_CHARS -> "long"
                else -> "medium"
            }
        return DerivedTrait(MESSAGE_LENGTH, value, (avg / LENGTH_SATURATION_CHARS).clamp01())
    }

    private fun emojiUsage(stats: CorpusStats): DerivedTrait {
        val perMessage = stats.emojiPerMessage
        val value =
            when {
                perMessage < EMOJI_NONE_BELOW -> "none"
                perMessage <= EMOJI_HIGH_ABOVE -> "low"
                else -> "high"
            }
        return DerivedTrait(EMOJI_USAGE, value, perMessage.clamp01())
    }

    private fun markerTrait(key: String, score: Double): DerivedTrait {
        val value =
            when {
                score < MARKER_LOW_BELOW -> "low"
                score < MARKER_HIGH_FROM -> "medium"
                else -> "high"
            }
        return DerivedTrait(key, value, score)
    }

    private fun initiative(stats: CorpusStats): DerivedTrait {
        val share = stats.initiativeShare
        val value =
            when {
                share > INITIATIVE_HIGH_ABOVE -> "high"
                share < INITIATIVE_LOW_BELOW -> "low"
                else -> "balanced"
            }
        return share(INITIATIVE, share, value)
    }

    private fun questions(stats: CorpusStats): DerivedTrait {
        val share = stats.questionShare
        val value =
            when {
                share > QUESTIONS_OFTEN_ABOVE -> "often"
                share > QUESTIONS_SOMETIMES_ABOVE -> "sometimes"
                else -> "rare"
            }
        return share(QUESTIONS, share, value)
    }

    private fun share(key: String, share: Double, value: String) = DerivedTrait(key, value, share.clamp01())

    companion object {
        const val REPLY_SPEED = "reply_speed"
        const val MESSAGE_LENGTH = "message_length"
        const val EMOJI_USAGE = "emoji_usage"
        const val CAPS = "caps"
        const val LOWERCASE_START = "lowercase_start"
        const val NIGHT_OWL = "night_owl"
        const val JEALOUSY = "jealousy"
        const val AFFECTION = "affection"
        const val INITIATIVE = "initiative"
        const val QUESTIONS = "questions"

        const val SHORT_BELOW_CHARS = 25.0
        const val LONG_ABOVE_CHARS = 80.0
        const val EMOJI_NONE_BELOW = 0.05
        const val EMOJI_HIGH_ABOVE = 0.5
        const val CAPS_OFTEN = 0.2
        const val NIGHT_OWL_SHARE = 0.25
        const val MARKER_LOW_BELOW = 0.2
        const val MARKER_HIGH_FROM = 0.5
        const val INITIATIVE_HIGH_ABOVE = 0.6
        const val INITIATIVE_LOW_BELOW = 0.4
        const val QUESTIONS_OFTEN_ABOVE = 0.3
        const val QUESTIONS_SOMETIMES_ABOVE = 0.1

        private const val HALF = 0.5
        private const val SLOW_SATURATION_SECONDS = 7_200.0
        private const val LENGTH_SATURATION_CHARS = 120.0

        private const val MARKER_SATURATION_SHARE = 0.2

        fun markerScore(markers: Int, theirMessages: Int): Double =
            if (theirMessages <= 0) 0.0 else (markers.toDouble() / theirMessages / MARKER_SATURATION_SHARE).clamp01()
    }
}
