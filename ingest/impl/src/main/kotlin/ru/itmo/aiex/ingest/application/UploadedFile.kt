package ru.itmo.aiex.ingest.application

import java.io.InputStream

class UploadedFile(val originalFilename: String?, val sizeBytes: Long, private val opener: () -> InputStream) {
    fun readBytes(): ByteArray = opener().use { it.readBytes() }
}
