package ru.itmo.aiex.ingest.infrastructure.parser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.ingest.testing.Fixtures
import java.time.Instant

class PlainTextParserTest {
    private val parser = PlainTextParser()

    @Test
    fun `Имя - текст с меткой времени и без неё, продолжение строки`() {
        val chat = parser.parse(Fixtures.bytes("plain/chat.txt"))
        assertThat(parser.source).isEqualTo(ImportSource.PLAIN_TEXT)
        assertThat(chat.messages.map { it.author to it.text }).containsExactly(
            "Маша" to "привет",
            "Я" to "привет, как ты?",
            "Маша" to "нормально",
            "Я" to "ок\nэто продолжение",
        )
        assertThat(chat.messages[0].sentAt).isEqualTo(Instant.parse("2024-01-01T12:00:00Z"))
        assertThat(chat.messages[0].localHour).isEqualTo(12)
        assertThat(chat.messages[2].sentAt).isNull()
        assertThat(chat.messages[2].localHour).isNull()
    }

    @Test
    fun `нет ни одной строки Имя - текст - MALFORMED_FILE`() {
        assertThatThrownBy { parser.parse(Fixtures.bytes("other/notes.txt")) }
            .isInstanceOf(ChatParseException::class.java)
            .hasFieldOrPropertyWithValue("code", ImportErrorCode.MALFORMED_FILE)
    }

    @Test
    fun `некорректная метка времени - строка считается продолжением`() {
        val chat = parser.parse("Маша: привет\n[2024-13-45 99:99] Маша: сломано".toByteArray())
        assertThat(chat.messages).hasSize(1)
        assertThat(chat.messages[0].text).isEqualTo("привет\n[2024-13-45 99:99] Маша: сломано")
    }
}
