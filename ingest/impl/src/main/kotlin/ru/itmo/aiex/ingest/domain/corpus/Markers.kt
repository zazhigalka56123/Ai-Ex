package ru.itmo.aiex.ingest.domain.corpus

object Markers {
    private fun wordStart(vararg phrases: String) = phrases.map { Regex("""(?iu)(?<!\p{L})${Regex.escape(it)}""") }

    val JEALOUSY: List<Regex> =
        wordStart(
            "где ты",
            "с кем",
            "кто она",
            "кто он",
            "кто это был",
            "почему не отвечаешь",
            "почему молчишь",
            "кому пишешь",
            "опять с ней",
            "опять с ним",
            "чья это",
            "ревну",
            "ревнова",
            "ревност",
        )

    val AFFECTION: List<Regex> =
        wordStart(
            "люблю", "любим", "скучаю", "скучала", "скучал", "котик", "котён",
            "котен", "солнце", "малыш", "целую", "обнимаю", "родной", "родная",
        ) +
            Regex("""(?iu)(?<!\p{L})(?:зай(?!\p{L})|зая(?!\p{L})|зайк|зайч)""") +
            listOf("❤", "😘", "🥰", "😍", "💋", "💕", "💖", "💗").map { Regex(Regex.escape(it)) }

    fun countMessages(bodies: List<String>, markers: List<Regex>): Int = bodies.count { body -> markers.any { it.containsMatchIn(body) } }
}
