package ru.itmo.aiex.common.id

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdsTest {
    @Test
    fun `генерируется UUIDv7 варианта RFC 9562`() {
        val id = Ids.next()
        assertThat(id.version()).isEqualTo(7)
        assertThat(id.variant()).isEqualTo(2)
    }

    @Test
    fun `старшие биты - время, поэтому идентификаторы разных миллисекунд упорядочены`() {
        val first = Ids.next()
        Thread.sleep(2)
        val second = Ids.next()
        assertThat(second.mostSignificantBits ushr 16).isGreaterThan(first.mostSignificantBits ushr 16)
        assertThat((1..1000).map { Ids.next() }.toSet()).hasSize(1000)
    }
}
