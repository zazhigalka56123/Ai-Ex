package ru.itmo.aiex.ingest.testing

object Fixtures {
    fun bytes(path: String): ByteArray =
        requireNotNull(Fixtures::class.java.getResourceAsStream("/fixtures/$path")) { "Нет фикстуры $path" }.use { it.readBytes() }
}
