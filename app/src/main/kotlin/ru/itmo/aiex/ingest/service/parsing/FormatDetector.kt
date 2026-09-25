package ru.itmo.aiex.ingest.service.parsing

import ru.itmo.aiex.common.error.UnsupportedImportFormatException
import ru.itmo.aiex.ingest.entity.ImportSource
object FormatDetector {
    private const val HEAD_BYTES = 64 * 1024
    private const val BINARY_PROBE_BYTES = 8 * 1024
    private const val SAMPLE_LINES = 20

    private val BINARY_EXTENSIONS =
        setOf(
            "pdf", "zip", "rar", "7z", "gz", "tar", "doc", "docx", "xls", "xlsx",
            "png", "jpg", "jpeg", "gif", "webp", "mp3", "mp4", "ogg", "opus", "exe",
        )

    private val MAGIC_NUMBERS =
        listOf(
            byteArrayOf(0x25, 0x50, 0x44, 0x46),
            byteArrayOf(0x50, 0x4B, 0x03, 0x04),
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
            byteArrayOf(0x47, 0x49, 0x46, 0x38),
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            byteArrayOf(0x1F, 0x8B.toByte()),
            byteArrayOf(0x52, 0x61, 0x72, 0x21),
        )

    fun detect(filename: String?, content: ByteArray): ImportSource {
        val extension = extensionOf(filename)
        rejectBinary(extension, content)
        val head = ChatText.decode(content.copyOfRange(0, minOf(content.size, HEAD_BYTES)))
        val firstChar = head.firstOrNull { !it.isWhitespace() }

        if (extension == "json" || firstChar == '{') return ImportSource.TELEGRAM_JSON
        val lines = head.lineSequence().map(ChatText::stripMarks).filter { it.isNotBlank() }.take(SAMPLE_LINES).toList()
        return when {
            lines.any { WhatsAppLine.parseHeader(it) != null } -> ImportSource.WHATSAPP_TXT

            lines.isNotEmpty() && PlainTextLine.parse(lines.first()) != null -> ImportSource.PLAIN_TEXT

            else -> throw UnsupportedImportFormatException(
                "Формат выгрузки не распознан. Поддерживаются: Telegram JSON (result.json одного чата), WhatsApp TXT, текст «Имя: сообщение»",
            )
        }
    }

    fun rejectBinary(extension: String?, content: ByteArray) {
        val binaryExtension = extension != null && extension in BINARY_EXTENSIONS
        if (binaryExtension || MAGIC_NUMBERS.any { content.startsWith(it) } || content.probe().any { it == 0.toByte() }) {
            throw UnsupportedImportFormatException("Бинарный файл${extension?.let { " .$it" } ?: ""} не является выгрузкой переписки")
        }
    }

    fun extensionOf(filename: String?): String? = filename?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun ByteArray.probe(): ByteArray = copyOfRange(0, minOf(size, BINARY_PROBE_BYTES))

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
