package ru.itmo.aiex.agent.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import ru.itmo.aiex.common.moderation.FlagReason

class GuardrailsTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "я больше не хочу жить",
            "Не  хочу\nжить без тебя",
            "думаю покончить с собой",
            "иногда хочется убить себя",
            "мысли о суициде",
            "это похоже на самоубийство",
            "я просто хочу умереть",
            "хочется порезать себя",
            "сегодня выйду в окно",
            "проще выпрыгнуть из окна",
            "решила свести счёты с жизнью",
            "ЖИТЬ НЕ ХОЧУ",
            "ну и выйди в окно",
            "убей себя",
        ],
    )
    fun `опасные темы распознаются как SELF_HARM без учёта регистра и ё`(text: String) {
        assertThat(Guardrails.inspect(text)?.reason).isEqualTo(FlagReason.SELF_HARM)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ты дура", "Ну и Идиот же ты", "мразь.", "какая же ты тварь!"])
    fun `оскорбления распознаются как ABUSE`(text: String) {
        assertThat(Guardrails.inspect(text)?.reason).isEqualTo(FlagReason.ABUSE)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "привет, спишь?",
            "я хочу жить у моря",
            "окно открыто, выйду в магазин",
            "сукно и дураковатый смех",
            "хочу в отпуск, устала",
            "",
        ],
    )
    fun `обычные сообщения проходят`(text: String) {
        assertThat(Guardrails.inspect(text)).isNull()
    }

    @Test
    fun `самое серьёзное срабатывание побеждает, а в находке - идентификатор правила, не текст`() {
        val finding = Guardrails.inspect("ты дура, я не хочу жить")
        assertThat(finding?.reason).isEqualTo(FlagReason.SELF_HARM)
        assertThat(finding?.rule).isEqualTo("self-harm.no-will-to-live")
    }

    @Test
    fun `нормализация - нижний регистр, ё в е, схлопнутые пробелы`() {
        assertThat(Guardrails.normalize("  Ёлка\t\tИ  ЁЖ ")).isEqualTo("елка и еж")
    }
}
