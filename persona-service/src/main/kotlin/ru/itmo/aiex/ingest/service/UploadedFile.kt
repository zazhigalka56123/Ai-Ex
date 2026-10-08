package ru.itmo.aiex.ingest.service

import java.io.InputStream

class UploadedFile(val originalFilename: String?, val sizeBytes: Long, private val opener: () -> InputStream) {
    fun readBytes(): ByteArray = opener().use { it.readBytes() }
}
