package ru.itmo.aiex.ingest.service.parsing

import ru.itmo.aiex.ingest.entity.ImportSource
import java.time.Instant

class ParsedChatBuilder(private val source: ImportSource) {
    private val messages = mutableListOf<ParsedMessage>()
    private var skipped = 0
    private var attachments = 0
    private var pending: Pending? = null

    val hasMessages: Boolean get() = messages.isNotEmpty() || pending != null

    fun message(author: String, text: String, sentAt: Instant?, localHour: Int?) {
        flush()
        pending = Pending(author, StringBuilder(text), sentAt, localHour)
    }

    fun system() {
        flush()
        skipped++
    }

    fun continuation(line: String) {
        pending?.text?.append('\n')?.append(line)
    }

    fun add(message: ParsedMessage?, attachment: Boolean) {
        flush()
        if (attachment) attachments++
        if (message == null || message.text.isBlank()) skipped++ else messages += message
    }

    fun build(chatName: String? = null): ParsedChat {
        flush()
        return ParsedChat(source, chatName, messages.toList(), skipped, attachments)
    }

    private fun flush() {
        val current = pending ?: return
        pending = null
        val text = current.text.toString().trim()
        when {
            AttachmentMarkers.isAttachment(text) -> {
                attachments++
                skipped++
            }

            text.isEmpty() || AttachmentMarkers.isDeleted(text) -> skipped++

            else -> messages += ParsedMessage(current.author, text, current.sentAt, current.localHour)
        }
    }

    private class Pending(val author: String, val text: StringBuilder, val sentAt: Instant?, val localHour: Int?)
}
