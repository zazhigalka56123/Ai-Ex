package ru.itmo.aiex.ingest.domain.corpus

import ru.itmo.aiex.ingest.domain.ImportErrorCode
import ru.itmo.aiex.ingest.domain.MessageAuthor
import ru.itmo.aiex.ingest.domain.parsing.ChatParseException
import ru.itmo.aiex.ingest.domain.parsing.ParsedChat

object AuthorResolver {
    private const val MESSAGE_MAX = 480

    fun resolve(chat: ParsedChat, explicitTheirName: String?, personaName: String): String {
        val authors = chat.authors
        fun find(name: String?): String? =
            name?.trim()?.takeIf { it.isNotEmpty() }?.let { wanted -> authors.firstOrNull { it.equals(wanted, ignoreCase = true) } }
        if (!explicitTheirName.isNullOrBlank()) {
            return find(explicitTheirName) ?: throw notDetected("Автор «${explicitTheirName.trim()}» в выгрузке не найден", authors)
        }
        return find(chat.chatName) ?: find(personaName)
            ?: throw notDetected("Не удалось определить, чьи сообщения принадлежат персоне - передайте theirName", authors)
    }

    fun normalize(chat: ParsedChat, theirName: String): List<CorpusMessage> = chat.messages.map {
        val author = if (it.author.equals(theirName, ignoreCase = true)) MessageAuthor.THEM else MessageAuthor.ME
        CorpusMessage(author, it.text, it.sentAt, it.localHour)
    }

    private fun notDetected(reason: String, authors: List<String>): ChatParseException {
        val message = "$reason. Авторы в файле: ${authors.joinToString(", ").ifEmpty { "-" }}"
        return ChatParseException(ImportErrorCode.AUTHOR_NOT_DETECTED, message.take(MESSAGE_MAX))
    }
}
