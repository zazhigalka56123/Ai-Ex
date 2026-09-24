package ru.itmo.aiex.persona.domain.profile

import ru.itmo.aiex.persona.api.CorpusStats

class AutoTagger {
    fun match(stats: CorpusStats, theirMessages: Int): List<DerivedTag> {
        val delay = stats.avgReplyDelaySeconds
        val speed = ReplySpeed.of(delay)
        val affection = TraitExtractor.markerScore(stats.affectionMarkers, theirMessages)
        val jealousy = TraitExtractor.markerScore(stats.jealousyMarkers, theirMessages)
        val short = stats.avgMessageLength < TraitExtractor.SHORT_BELOW_CHARS
        val long = stats.avgMessageLength > TraitExtractor.LONG_ABOVE_CHARS
        return buildList {
            if (isCold(short, speed, stats.emojiPerMessage, affection)) add(DerivedTag(COLD, COLD_WEIGHT))
            if (jealousy >= TraitExtractor.MARKER_HIGH_FROM) add(DerivedTag(JEALOUS, jealousy))
            if (stats.capsShare > TraitExtractor.CAPS_OFTEN) add(DerivedTag(CAPS, (stats.capsShare * 2).clamp01()))
            if (stats.emojiPerMessage > TraitExtractor.EMOJI_HIGH_ABOVE) add(DerivedTag(EMOJI, (stats.emojiPerMessage / 2).clamp01()))
            if (stats.nightShare > TraitExtractor.NIGHT_OWL_SHARE) add(DerivedTag(NIGHT_OWL, (stats.nightShare * 2).clamp01()))
            if (short) add(DerivedTag(LACONIC, (1 - stats.avgMessageLength / TraitExtractor.SHORT_BELOW_CHARS).clamp01()))
            if (long) add(DerivedTag(TALKATIVE, (stats.avgMessageLength / TALKATIVE_SATURATION_CHARS).clamp01()))
            if (speed == ReplySpeed.SLOW && delay != null) add(DerivedTag(SLOW_REPLIER, (delay.toDouble() / SLOW_SATURATION_SECONDS).clamp01()))
            if (speed == ReplySpeed.FAST && delay != null) {
                add(DerivedTag(FAST_REPLIER, (1 - delay.toDouble() / ReplySpeed.FAST_BELOW_SECONDS).clamp01()))
            }
            if (affection >= TraitExtractor.MARKER_HIGH_FROM) add(DerivedTag(AFFECTIONATE, affection))
        }
    }

    private fun isCold(short: Boolean, speed: ReplySpeed, emojiPerMessage: Double, affection: Double): Boolean {
        val distant = short && speed == ReplySpeed.SLOW
        val dry = emojiPerMessage <= TraitExtractor.EMOJI_HIGH_ABOVE && affection < TraitExtractor.MARKER_LOW_BELOW
        return distant && dry
    }

    companion object {
        const val COLD = "cold"
        const val JEALOUS = "jealous"
        const val CAPS = "caps"
        const val EMOJI = "emoji"
        const val NIGHT_OWL = "night-owl"
        const val LACONIC = "laconic"
        const val TALKATIVE = "talkative"
        const val SLOW_REPLIER = "slow-replier"
        const val FAST_REPLIER = "fast-replier"
        const val AFFECTIONATE = "affectionate"

        private const val COLD_WEIGHT = 0.8
        private const val TALKATIVE_SATURATION_CHARS = 200.0
        private const val SLOW_SATURATION_SECONDS = 7_200.0
    }
}
