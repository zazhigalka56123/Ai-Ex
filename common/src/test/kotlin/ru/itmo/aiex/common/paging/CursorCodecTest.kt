package ru.itmo.aiex.common.paging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.common.error.ValidationException
import java.time.Instant
import java.util.Base64
import java.util.UUID

class CursorCodecTest {
    @Test
    fun `курсор по времени и id переживает кодирование с точностью до микросекунд`() {
        val position = TimeIdPosition(Instant.parse("2026-09-22T11:04:00.123456Z"), UUID.randomUUID())
        val cursor = CursorCodec.encode(position)
        assertThat(cursor).doesNotContain("=", "+", "/")
        assertThat(CursorCodec.decodeTimeId(cursor)).isEqualTo(position)
    }

    @Test
    fun `время до эпохи Unix тоже кодируется корректно`() {
        val position = TimeIdPosition(Instant.parse("1969-12-31T23:59:59.999999Z"), UUID.randomUUID())
        assertThat(CursorCodec.decodeTimeId(CursorCodec.encode(position))).isEqualTo(position)
    }

    @Test
    fun `порядковый курсор`() {
        assertThat(CursorCodec.decodeOrdinal(CursorCodec.encodeOrdinal(4242))).isEqualTo(4242)
    }

    @Test
    fun `повреждённый курсор - ошибка валидации поля cursor`() {
        val garbage =
            listOf("%%%", Base64.getUrlEncoder().encodeToString("abc".toByteArray()), Base64.getUrlEncoder().encodeToString("1:2:3".toByteArray()))
        garbage.forEach { cursor ->
            assertThatThrownBy { CursorCodec.decodeTimeId(cursor) }
                .isInstanceOf(ValidationException::class.java)
                .satisfies({ assertThat((it as ValidationException).violations.single().field).isEqualTo("cursor") })
        }
        assertThatThrownBy { CursorCodec.decodeOrdinal(Base64.getUrlEncoder().encodeToString("x".toByteArray())) }
            .isInstanceOf(ValidationException::class.java)
    }
}
