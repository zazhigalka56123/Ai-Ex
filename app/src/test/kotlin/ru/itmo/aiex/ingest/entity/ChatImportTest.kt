package ru.itmo.aiex.ingest.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import ru.itmo.aiex.ingest.service.MessageRow
import ru.itmo.aiex.ingest.service.ParseResult
import java.time.Instant
import java.util.UUID

class ChatImportTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private fun chatImport() =
        ChatImport(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ImportSource.TELEGRAM_JSON, "result.json", 100, now)

    @Test
    fun `PENDING, PARSING, PARSED со статистикой`() {
        val chatImport = chatImport()
        assertThat(chatImport.status).isEqualTo(ImportStatus.PENDING)
        chatImport.startParsing()
        chatImport.markParsed(ParseResult(10, 4, 2, "Маша"), now)
        assertThat(chatImport.status).isEqualTo(ImportStatus.PARSED)
        assertThat(chatImport.messageCount).isEqualTo(10)
        assertThat(chatImport.theirMessageCount).isEqualTo(4)
        assertThat(chatImport.skippedCount).isEqualTo(2)
        assertThat(chatImport.theirName).isEqualTo("Маша")
        assertThat(chatImport.finishedAt).isEqualTo(now)
        assertThat(chatImport.errorCode).isNull()

        chatImport.markRebuildFailed("LLM недоступен")
        assertThat(chatImport.status).isEqualTo(ImportStatus.PARSED)
        assertThat(chatImport.errorCode).isEqualTo(ImportErrorCode.PROFILE_REBUILD_FAILED)
    }

    @Test
    fun `падение пишет код и обрезает сообщение, повтор - no-op, PARSED упасть не может`() {
        val chatImport = chatImport()
        chatImport.markFailed(ImportErrorCode.MALFORMED_FILE, "x".repeat(600), now)
        assertThat(chatImport.status).isEqualTo(ImportStatus.FAILED)
        assertThat(chatImport.errorMessage).hasSize(ChatImport.ERROR_MESSAGE_MAX)
        chatImport.markFailed(ImportErrorCode.INTERNAL, "другое", now)
        assertThat(chatImport.errorCode).isEqualTo(ImportErrorCode.MALFORMED_FILE)

        val parsed = chatImport().apply {
            startParsing()
            markParsed(ParseResult(1, 1, 0, "Маша"), now)
        }
        assertThatThrownBy { parsed.markFailed(ImportErrorCode.INTERNAL, "поздно", now) }
            .isInstanceOf(IllegalStateTransitionException::class.java)
            .hasFieldOrPropertyWithValue("code", ErrorCode.IMPORT_INVALID_STATE)
    }

    @Test
    fun `недопустимые переходы - IMPORT_INVALID_STATE`() {
        assertThatThrownBy { chatImport().markParsed(ParseResult(1, 1, 0, "Маша"), now) }.isInstanceOf(IllegalStateTransitionException::class.java)
        assertThatThrownBy { chatImport().apply { startParsing() }.startParsing() }.isInstanceOf(IllegalStateTransitionException::class.java)
        assertThatThrownBy { chatImport().markRebuildFailed("нет") }.isInstanceOf(IllegalStateTransitionException::class.java)
    }

    @Test
    fun `тело сообщения не попадает в toString`() {
        val message = ImportedMessage(UUID.randomUUID(), chatImport(), MessageAuthor.THEM, "секрет", now, 0)
        assertThat(message.toString()).doesNotContain("секрет")
        assertThat(MessageRow(MessageAuthor.ME, "секрет", null, 1).toString()).doesNotContain("секрет")
        assertThat(message).isEqualTo(message).isNotEqualTo(ImportedMessage(UUID.randomUUID(), chatImport(), MessageAuthor.ME, "x", null, 1))
    }
}
