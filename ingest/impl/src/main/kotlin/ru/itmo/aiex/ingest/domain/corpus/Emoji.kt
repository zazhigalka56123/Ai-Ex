package ru.itmo.aiex.ingest.domain.corpus

object Emoji {
    private const val ZWJ = 0x200D
    private val PICTOGRAPHS = 0x1F300..0x1FAFF
    private val SKIN_TONES = 0x1F3FB..0x1F3FF
    private val SYMBOLS = 0x2600..0x27BF
    private val EXTRA = setOf(0x2B50, 0x2B55, 0x2B06, 0x2B07, 0x2934, 0x2935, 0x3030, 0x303D)

    fun extract(text: String): List<String> {
        val result = mutableListOf<String>()
        var previous = -1
        text.codePoints().forEach { cp ->
            if (previous != ZWJ && isEmoji(cp)) result += String(Character.toChars(cp))
            previous = cp
        }
        return result
    }

    fun isEmoji(codePoint: Int): Boolean = (codePoint in PICTOGRAPHS && codePoint !in SKIN_TONES) || codePoint in SYMBOLS || codePoint in EXTRA
}
