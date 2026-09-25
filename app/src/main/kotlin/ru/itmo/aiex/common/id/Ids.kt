package ru.itmo.aiex.common.id

import java.security.SecureRandom
import java.util.UUID

object Ids {
    private val random = SecureRandom()

    private const val RANDOM_BYTES = 10
    private const val VERSION_7 = 0x7000L
    private const val NIBBLE = 0x0FL
    private const val BYTE = 0xFFL
    private const val VARIANT_MASK = 0x3FFFFFFFFFFFFFFFL
    private const val TIMESTAMP_SHIFT = 16
    private const val BYTE_BITS = 8

    fun next(): UUID {
        val millis = System.currentTimeMillis()
        val bytes = ByteArray(RANDOM_BYTES).also(random::nextBytes)
        val msb = (millis shl TIMESTAMP_SHIFT) or VERSION_7 or ((bytes[0].toLong() and NIBBLE) shl BYTE_BITS) or (bytes[1].toLong() and BYTE)
        var lsb = 0L
        for (i in 2 until RANDOM_BYTES) {
            lsb = (lsb shl BYTE_BITS) or (bytes[i].toLong() and BYTE)
        }
        lsb = (lsb and VARIANT_MASK) or Long.MIN_VALUE
        return UUID(msb, lsb)
    }
}
