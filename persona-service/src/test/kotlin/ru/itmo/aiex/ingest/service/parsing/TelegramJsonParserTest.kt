package ru.itmo.aiex.ingest.service.parsing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.ingest.entity.ImportErrorCode
import ru.itmo.aiex.ingest.entity.ImportSource
import ru.itmo.aiex.ingest.testing.Fixtures
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

class TelegramJsonParserTest {
    private val parser = TelegramJsonParser(JsonMapper.builder().build())

    @Test
    fun `личный чат - сообщения по порядку, служебные и пустые пропущены, медиа посчитаны`() {
        val chat = parser.parse(Fixtures.bytes("telegram/personal_chat.json"))

        assertThat(parser.source).isEqualTo(ImportSource.TELEGRAM_JSON)
        assertThat(chat.chatName).isEqualTo("Маша")
        assertThat(chat.messages).hasSize(25)
        assertThat(chat.skipped).isEqualTo(3)
        assertThat(chat.attachments).isEqualTo(3)
        assertThat(chat.authors).containsExactly("Алексей", "Маша")
        assertThat(chat.messages.count { it.author == "Маша" }).isEqualTo(15)

        val first = chat.messages.first()
        assertThat(first.text).isEqualTo("привет, спишь?")
        assertThat(first.sentAt).isEqualTo(Instant.parse("2024-03-01T20:10:00Z"))
        assertThat(first.localHour).isEqualTo(23)
    }

    @Test
    fun `text-массив из строк и объектов склеивается`() {
        val texts = parser.parse(Fixtures.bytes("telegram/personal_chat.json")).messages.map { it.text }
        assertThat(texts).contains("смотри https://example.com/party", "ты вообще меня слушаешь?", "ну смотри мне 😂😂")
    }

    @Test
    fun `битый JSON - MALFORMED_FILE`() {
        assertThatThrownBy { parser.parse(Fixtures.bytes("telegram/broken.json")) }
            .isInstanceOf(ChatParseException::class.java)
            .hasFieldOrPropertyWithValue("code", ImportErrorCode.MALFORMED_FILE)
            .hasMessageContaining("JSON")
    }

    @Test
    fun `выгрузка всего аккаунта - MALFORMED_FILE с подсказкой`() {
        assertThatThrownBy { parser.parse(Fixtures.bytes("telegram/full_account.json")) }
            .isInstanceOf(ChatParseException::class.java)
            .hasFieldOrPropertyWithValue("code", ImportErrorCode.MALFORMED_FILE)
            .hasMessageContaining("всего аккаунта")
    }

    @Test
    fun `пустая выгрузка разбирается в пустой список`() {
        val chat = parser.parse(Fixtures.bytes("telegram/empty.json"))
        assertThat(chat.messages).isEmpty()
        assertThat(chat.chatName).isEqualTo("Маша")
    }

    @Test
    fun `не объект и нет messages - MALFORMED_FILE`() {
        listOf("[1, 2]", """{"name": "x"}""", """{"messages": {}}""", "null").forEach { raw ->
            assertThatThrownBy { parser.parse(raw.toByteArray()) }
                .describedAs(raw)
                .isInstanceOf(ChatParseException::class.java)
                .hasFieldOrPropertyWithValue("code", ImportErrorCode.MALFORMED_FILE)
        }
    }

    @Test
    fun `без date_unixtime время берётся из date, без from - из from_id, группа не даёт имени собеседника`() {
        val raw =
            """
            {"name": "Семья", "type": "private_group", "messages": [
              {"type": "message", "date": "2024-01-01T02:30:00", "from_id": "user1", "text": "ночью"},
              {"type": "message", "date": "не дата", "from": "Маша", "text": "без времени"},
              {"type": "message", "from": "Маша", "text": 42},
              "мусор"
            ]}
            """.trimIndent()
        val chat = parser.parse(raw.toByteArray())
        assertThat(chat.chatName).isNull()
        assertThat(chat.messages).hasSize(2)
        assertThat(chat.messages[0].author).isEqualTo("user1")
        assertThat(chat.messages[0].sentAt).isEqualTo(Instant.parse("2024-01-01T02:30:00Z"))
        assertThat(chat.messages[0].localHour).isEqualTo(2)
        assertThat(chat.messages[1].sentAt).isNull()
        assertThat(chat.messages[1].localHour).isNull()
        assertThat(chat.skipped).isEqualTo(2)
    }
}
