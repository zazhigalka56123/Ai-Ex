package ru.itmo.aiex.ingest.domain.corpus

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.domain.MessageAuthor
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.ingest.domain.parsing.ParsedChat
import ru.itmo.aiex.ingest.domain.parsing.ParsedMessage

class AuthorResolverTest {
    private fun chat(chatName: String? = null, vararg authors: String) =
        ParsedChat(ImportSource.WHATSAPP_TXT, chatName, authors.map { ParsedMessage(it, "текст", null, null) }, 0, 0)

    @Test
    fun `явный theirName важнее всего, регистр не важен`() {
        assertThat(AuthorResolver.resolve(chat("Маша", "Алексей", "Маша"), " алексей ", "Маша")).isEqualTo("Алексей")
    }

    @Test
    fun `имя собеседника из выгрузки, затем имя персоны`() {
        assertThat(AuthorResolver.resolve(chat("маша", "Алексей", "Маша"), null, "Другая")).isEqualTo("Маша")
        assertThat(AuthorResolver.resolve(chat(null, "Алексей", "Мария"), null, "МАРИЯ")).isEqualTo("Мария")
    }

    @Test
    fun `автор не найден - AUTHOR_NOT_DETECTED со списком авторов`() {
        assertThatThrownBy { AuthorResolver.resolve(chat(null, "Алексей", "Мария"), null, "Маша") }
            .isInstanceOf(ChatParseException::class.java)
            .hasFieldOrPropertyWithValue("code", ImportErrorCode.AUTHOR_NOT_DETECTED)
            .hasMessageContaining("Алексей, Мария")
        assertThatThrownBy { AuthorResolver.resolve(chat(null, "Алексей"), "Катя", "Маша") }
            .isInstanceOf(ChatParseException::class.java)
            .hasMessageContaining("«Катя»")
    }

    @Test
    fun `нормализация - THEM только у выбранного автора, остальные ME`() {
        val normalized = AuthorResolver.normalize(chat(null, "Алексей", "Маша", "Третий", "МАША"), "Маша")
        assertThat(normalized.map { it.author }).containsExactly(MessageAuthor.ME, MessageAuthor.THEM, MessageAuthor.ME, MessageAuthor.THEM)
        assertThat(normalized.first().toString()).doesNotContain("текст")
    }
}
