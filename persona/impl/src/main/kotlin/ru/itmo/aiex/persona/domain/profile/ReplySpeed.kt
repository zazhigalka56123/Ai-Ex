package ru.itmo.aiex.persona.domain.profile

enum class ReplySpeed(val code: String, val title: String) {
    FAST("fast", "отвечает почти сразу"),
    MEDIUM("medium", "отвечает в пределах получаса"),
    SLOW("slow", "отвечает медленно, может пропасть на часы"),
    UNKNOWN("unknown", "скорость ответа неизвестна"),
    ;

    companion object {
        const val FAST_BELOW_SECONDS = 120L
        const val MEDIUM_BELOW_SECONDS = 1_800L

        fun of(avgReplyDelaySeconds: Long?): ReplySpeed = when {
            avgReplyDelaySeconds == null -> UNKNOWN
            avgReplyDelaySeconds < FAST_BELOW_SECONDS -> FAST
            avgReplyDelaySeconds < MEDIUM_BELOW_SECONDS -> MEDIUM
            else -> SLOW
        }
    }
}
