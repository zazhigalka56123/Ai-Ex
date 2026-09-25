package ru.itmo.aiex.ingest.service.parsing

object AttachmentMarkers {
    private val ATTACHMENT =
        setOf(
            "<media omitted>",
            "<без медиафайлов>",
            "<медиафайл отсутствует>",
            "<медиа пропущено>",
            "изображение отсутствует",
            "видео отсутствует",
            "аудиофайл отсутствует",
            "стикер отсутствует",
            "gif отсутствует",
            "документ отсутствует",
            "null",
        )
    private val ATTACHMENT_PATTERN =
        Regex("""(?i)^(?:<(?:attached|прикреплено):.*>|.{0,120}\b(?:image|video|audio|sticker|gif|document|contact card) omitted)$""")
    private val DELETED =
        setOf("this message was deleted", "you deleted this message", "данное сообщение удалено", "это сообщение удалено", "вы удалили это сообщение")

    fun isAttachment(text: String): Boolean = text.lowercase() in ATTACHMENT || ATTACHMENT_PATTERN.matches(text)

    fun isDeleted(text: String): Boolean = text.lowercase() in DELETED
}
