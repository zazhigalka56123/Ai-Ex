package ru.itmo.aiex.ingest.infrastructure.parser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.ingest.testing.Fixtures
import java.time.Instant

class WhatsAppTxtParserTest {
    private val parser = WhatsAppTxtParser()

    @Test
    fun `Android RU - многострочные сообщения, системная строка, медиа и удалённое пропущены`() {
        val chat = parser.parse(Fixtures.bytes("whatsapp/android_ru.txt"))

        assertThat(parser.source).isEqualTo(ImportSource.WHATSAPP_TXT)
        assertThat(chat.chatName).isNull()
        assertThat(chat.messages.map { it.author to it.text }).containsExactly(
            "Алексей" to "с наступающим!",
            "Маша" to "и тебя",
            "Алексей" to "как отмечаешь?",
            "Маша" to "дома\nсижу с котом\nсмотрю фильм",
            "Маша" to "а ты где? с кем?",
            "Алексей" to "с друзьями",
            "Маша" to "ну ок",
        )
        assertThat(chat.skipped).isEqualTo(3)
        assertThat(chat.attachments).isEqualTo(1)
        assertThat(chat.messages[1].sentAt).isEqualTo(Instant.parse("2023-12-31T23:55:00Z"))
        assertThat(chat.messages[3].localHour).isEqualTo(0)
    }

    @Test
    fun `Android EN - месяц первым и 12-часовой формат`() {
        val chat = parser.parse(Fixtures.bytes("whatsapp/android_en.txt"))
        assertThat(
            chat.messages.map {
                it.text
            },
        ).containsExactly("happy new year!", "you too", "how are you celebrating?", "at home\nwith my cat", "see you")
        assertThat(chat.messages[0].sentAt).isEqualTo(Instant.parse("2023-12-31T23:41:00Z"))
        assertThat(chat.messages[2].sentAt).isEqualTo(Instant.parse("2024-01-01T00:05:00Z"))
        assertThat(chat.messages[4].localHour).isEqualTo(12)
        assertThat(chat.attachments).isEqualTo(1)
    }

    @Test
    fun `iOS - квадратные скобки, секунды, невидимые U+200E и omitted-вложения`() {
        val chat = parser.parse(Fixtures.bytes("whatsapp/ios.txt"))
        assertThat(chat.messages.map { it.author to it.text }).containsExactly(
            "Алексей" to "с наступающим!",
            "Маша" to "и тебя 🎄",
            "Маша" to "дома\nа ты?",
            "Алексей" to "тоже дома",
        )
        assertThat(chat.attachments).isEqualTo(2)
        assertThat(chat.skipped).isEqualTo(2)
        assertThat(chat.messages[0].sentAt).isEqualTo(Instant.parse("2023-12-31T23:41:05Z"))
    }

    @Test
    fun `файл без строк WhatsApp - MALFORMED_FILE`() {
        assertThatThrownBy { parser.parse("просто текст\nбез дат".toByteArray()) }
            .isInstanceOf(ChatParseException::class.java)
            .hasFieldOrPropertyWithValue("code", ImportErrorCode.MALFORMED_FILE)
    }
}
