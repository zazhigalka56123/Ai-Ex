package ru.itmo.aiex.ingest.service.corpus

object PrivacyFilter {
    private val PATTERNS =
        listOf(
            Regex("""(?i)(https?://|www\.)"""),
            Regex("""(?i)\b[\w-]+\.(ru|com|org|net|io|me|рф|su|info)\b"""),
            Regex("""[\w.+-]+@[\w-]+\.[\w.-]+"""),
            Regex("""(?<![\w])@\w{3,}"""),
            Regex("""\+?\d[\d\s()\-]{8,}\d"""),
            Regex("""\d{5,}"""),
        )

    fun isSafe(text: String): Boolean = PATTERNS.none { it.containsMatchIn(text) }
}
