package ru.itmo.aiex.agent.domain

import ru.itmo.aiex.common.moderation.FlagReason

object Guardrails {
    private class Rule(val id: String, val reason: FlagReason, vararg alternatives: String) {
        val pattern = Regex("(?U)" + alternatives.joinToString("|") { "(?:$it)" })
    }

    private val rules =
        listOf(
            Rule(
                "self-harm.no-will-to-live",
                FlagReason.SELF_HARM,
                """\bне\s+хочу\s+(?:больше\s+)?жить\b""",
                """\bжить\s+(?:больше\s+)?не\s+хочу\b""",
                """\bне\s+(?:вижу\s+)?смысла\s+жить\b""",
                """\bнет\s+смысла\s+жить\b""",
            ),
            Rule(
                "self-harm.suicide",
                FlagReason.SELF_HARM,
                """\bпоконч\w*\s+с\s+собой\b""",
                """\bсуицид\w*""",
                """\bсамоубийств\w*""",
                """\bналожить\s+на\s+себя\s+руки\b""",
                """\bсвести\s+счеты\s+с\s+жизнью\b""",
            ),
            Rule("self-harm.kill-self", FlagReason.SELF_HARM, """\b(?:убить|убью|убей|убиваю)\s+себя\b"""),
            Rule("self-harm.want-to-die", FlagReason.SELF_HARM, """\bхочу\s+умереть\b""", """\bлучше\s+бы\s+я\s+умер(?:ла)?\b"""),
            Rule(
                "self-harm.cutting",
                FlagReason.SELF_HARM,
                """\b(?:порезать|порежу|резать|режу)\s+себя\b""",
                """\b(?:порезать|вскрыть|вскрою)\s+(?:себе\s+)?вены\b""",
            ),
            Rule(
                "self-harm.window",
                FlagReason.SELF_HARM,
                """\b(?:выйти|выйду|выйди|шагнуть|шагну|шагни|выпрыгнуть|выпрыгну|выпрыгни|прыгнуть|прыгну|прыгни)\s+(?:в|из)\s+окн\w*""",
            ),
            Rule(
                "abuse.insult",
                FlagReason.ABUSE,
                """\b(?:дура|дурак|идиот|идиотка|дебил|дебилка|кретин|тупица|урод|уродина|тварь|мразь|сука|ничтожество)\b""",
            ),
        )

    private val whitespace = Regex("\\s+")

    fun normalize(text: String): String = text.lowercase().replace('ё', 'е').replace(whitespace, " ").trim()

    fun inspect(text: String): GuardrailFinding? {
        val normalized = normalize(text)
        return rules.firstOrNull { it.pattern.containsMatchIn(normalized) }?.let { GuardrailFinding(it.reason, it.id) }
    }
}
