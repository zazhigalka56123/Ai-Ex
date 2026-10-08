package ru.itmo.aiex.ingest.service.parsing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.aiex.ingest.entity.ImportSource
import java.time.LocalDateTime

class GrammarTest {
    @Test
    fun `заголовки WhatsApp - Android и iOS, дата по разделителю, AM и PM`() {
        val ru = requireNotNull(WhatsAppLine.parseHeader("31.12.2023, 23:59 - Маша: привет: как дела"))
        assertThat(ru.time).isEqualTo(LocalDateTime.of(2023, 12, 31, 23, 59))
        assertThat(ru.author).isEqualTo("Маша")
        assertThat(ru.text).isEqualTo("привет: как дела")

        assertThat(WhatsAppLine.parseHeader("12/31/23, 12:15 AM - Alex: hi")?.time).isEqualTo(LocalDateTime.of(2023, 12, 31, 0, 15))
        assertThat(WhatsAppLine.parseHeader("1/2/24, 3:07 pm - Alex: hi")?.time).isEqualTo(LocalDateTime.of(2024, 1, 2, 15, 7))
        assertThat(WhatsAppLine.parseHeader("[01.02.2024, 09:05:07] Маша: утро")?.time).isEqualTo(LocalDateTime.of(2024, 2, 1, 9, 5, 7))

        val system = requireNotNull(WhatsAppLine.parseHeader("31.12.2023, 23:40 - Сообщения защищены шифрованием"))
        assertThat(system.author).isNull()
    }

    @Test
    fun `не заголовки - продолжение сообщения`() {
        assertThat(WhatsAppLine.parseHeader("просто строка")).isNull()
        assertThat(WhatsAppLine.parseHeader("31.13.2023, 23:59 - Маша: несуществующая дата")).isNull()
        assertThat(PlainTextLine.parse("без двоеточия")).isNull()
        assertThat(PlainTextLine.parse("https://example.com")).isNull()
    }

    @Test
    fun `заглушки вложений и удалённых сообщений`() {
        listOf(
            "<Media omitted>",
            "<Без медиафайлов>",
            "image omitted",
            "IMG-2024.jpg (file attached) document omitted",
            "<attached: 00000012-PHOTO.jpg>",
        )
            .forEach { assertThat(AttachmentMarkers.isAttachment(it)).describedAs(it).isTrue() }
        assertThat(AttachmentMarkers.isAttachment("я сегодня omitted всё")).isFalse()
        assertThat(AttachmentMarkers.isDeleted("This message was deleted")).isTrue()
        assertThat(AttachmentMarkers.isDeleted("удали это")).isFalse()
    }

    @Test
    fun `накопитель закрывает сообщение только на следующем заголовке`() {
        val builder = ParsedChatBuilder(ImportSource.PLAIN_TEXT)
        builder.continuation("до первого сообщения - игнор")
        assertThat(builder.hasMessages).isFalse()
        builder.message("Маша", "раз", null, null)
        builder.continuation("два")
        builder.system()
        builder.continuation("после системной строки - игнор")
        builder.message("Маша", "  ", null, null)
        val chat = builder.build()
        assertThat(chat.messages.map { it.text }).containsExactly("раз\nдва")
        assertThat(chat.skipped).isEqualTo(2)
    }
}
