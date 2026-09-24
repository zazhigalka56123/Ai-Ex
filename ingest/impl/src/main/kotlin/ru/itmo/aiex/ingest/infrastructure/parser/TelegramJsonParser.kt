package ru.itmo.aiex.ingest.infrastructure.parser

import org.springframework.stereotype.Component
import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.ImportSource
import ru.itmo.aiex.ingest.domain.parsing.ChatExportParser
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.ingest.domain.parsing.ParsedChat
import ru.itmo.aiex.ingest.domain.parsing.ParsedChatBuilder
import ru.itmo.aiex.ingest.domain.parsing.ParsedMessage
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Component
internal class TelegramJsonParser(private val mapper: JsonMapper) : ChatExportParser {
    override val source: ImportSource = ImportSource.TELEGRAM_JSON

    override fun parse(content: ByteArray): ParsedChat {
        val root =
            try {
                mapper.readTree(content)
            } catch (ex: JacksonException) {
                throw ChatParseException(ImportErrorCode.MALFORMED_FILE, "Файл не является корректным JSON: ${ex.originalMessage}", ex)
            }
        if (root == null || !root.isObject) throw malformed("Ожидался JSON-объект выгрузки одного чата Telegram")
        if (root.has("chats")) {
            throw malformed("Это выгрузка всего аккаунта Telegram. Выгрузите один личный чат: меню чата -> «Экспорт истории чата» -> JSON")
        }
        val messages = root.get("messages")
        if (messages == null || !messages.isArray) throw malformed("В выгрузке Telegram нет массива messages")

        val builder = ParsedChatBuilder(source)
        messages.forEach { node ->
            val isMessage = node.isObject && node.string("type") == "message"
            builder.add(if (isMessage) toMessage(node) else null, attachment = isMessage && MEDIA_FIELDS.any { node.has(it) })
        }
        val chatType = root.string("type")
        val chatName = root.string("name")?.trim()?.takeIf { it.isNotEmpty() && (chatType == null || chatType == PERSONAL_CHAT) }
        return builder.build(chatName)
    }

    private fun toMessage(node: JsonNode): ParsedMessage? {
        val text = text(node.get("text")).trim()
        if (text.isEmpty()) return null
        val author = node.string("from")?.trim()?.takeIf { it.isNotEmpty() } ?: node.string("from_id") ?: UNKNOWN_AUTHOR
        val local = node.string("date")?.let(::parseLocal)
        val sentAt = node.get("date_unixtime")?.asString()?.toLongOrNull()?.let(Instant::ofEpochSecond) ?: local?.toInstant(ZoneOffset.UTC)
        return ParsedMessage(author, text, sentAt, local?.hour)
    }

    private fun text(node: JsonNode?): String = when {
        node == null || node.isNull -> ""
        node.isString -> node.asString()
        node.isArray -> node.joinToString("") { part -> if (part.isString) part.asString() else part.string("text").orEmpty() }
        else -> ""
    }

    private fun parseLocal(raw: String): LocalDateTime? = try {
        LocalDateTime.parse(raw)
    } catch (_: DateTimeException) {
        null
    }

    private fun JsonNode.string(field: String): String? = get(field)?.takeIf { it.isString }?.asString()

    private fun malformed(message: String) = ChatParseException(ImportErrorCode.MALFORMED_FILE, message)

    private companion object {
        const val PERSONAL_CHAT = "personal_chat"
        const val UNKNOWN_AUTHOR = "Неизвестный"
        val MEDIA_FIELDS = listOf("photo", "file", "media_type", "sticker_emoji", "poll", "location_information", "contact_information")
    }
}
