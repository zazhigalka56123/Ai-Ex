package ru.itmo.aiex.common.paging

import ru.itmo.aiex.common.error.ValidationException
import java.time.Instant
import java.util.Base64
import java.util.UUID

object CursorCodec {
    private const val MICROS_PER_SECOND = 1_000_000L
    private const val NANOS_PER_MICRO = 1_000L

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(position: TimeIdPosition): String {
        val micros = Math.multiplyExact(position.timestamp.epochSecond, MICROS_PER_SECOND) +
            position.timestamp.nano / NANOS_PER_MICRO
        return encodeRaw("$micros:${position.id}")
    }

    fun decodeTimeId(cursor: String): TimeIdPosition {
        val raw = decodeRaw(cursor)
        val parts = raw.split(':')
        val micros = parts.getOrNull(0)?.toLongOrNull()
        val id = parts.getOrNull(1)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (parts.size != 2 || micros == null || id == null) invalid()
        val instant = Instant.ofEpochSecond(Math.floorDiv(micros, MICROS_PER_SECOND), Math.floorMod(micros, MICROS_PER_SECOND) * NANOS_PER_MICRO)
        return TimeIdPosition(instant, id)
    }

    fun encodeOrdinal(ordinal: Long): String = encodeRaw(ordinal.toString())

    fun decodeOrdinal(cursor: String): Long = decodeRaw(cursor).toLongOrNull() ?: invalid()

    private fun encodeRaw(raw: String): String = encoder.encodeToString(raw.toByteArray(Charsets.UTF_8))

    private fun decodeRaw(cursor: String): String = try {
        String(decoder.decode(cursor), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        invalid()
    }

    private fun invalid(): Nothing = throw ValidationException("cursor", "cursor.invalid", "Курсор повреждён или получен не от этого API")
}
