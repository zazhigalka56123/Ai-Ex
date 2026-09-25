package ru.itmo.aiex.common.error

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ErrorCodeTest {
    @Test
    fun `слаг кода для поля type`() {
        assertThat(ErrorCode.PERSONA_NOT_READY.slug).isEqualTo("persona-not-ready")
    }

    @Test
    fun `422 не используется, все not found - 404, все переходы - 409`() {
        assertThat(ErrorCode.entries.map { it.httpStatus }.toSet()).doesNotContain(422)
        assertThat(ErrorCode.entries.filter { it.name.endsWith("_NOT_FOUND") }).allMatch { it.httpStatus == 404 }
        assertThat(ErrorCode.entries.filter { it.name.endsWith("_INVALID_STATE") }).allMatch { it.httpStatus == 409 }
    }

    @Test
    fun `исключения несут код и нарушения`() {
        val validation = ValidationException("size", "size.range", "слишком много")
        assertThat(validation.code).isEqualTo(ErrorCode.VALIDATION_FAILED)
        assertThat(validation.message).isEqualTo("size: слишком много")
        assertThat(NotFoundException.of(ErrorCode.PERSONA_NOT_FOUND, 42).message).contains("42")
        assertThat(IllegalStateTransitionException(ErrorCode.PERSONA_INVALID_STATE, "DRAFT", "READY").message).contains("DRAFT -> READY")
        assertThat(SlotAlreadyBookedException().code).isEqualTo(ErrorCode.SLOT_TAKEN)
        assertThat(LlmUnavailableException("down").code.httpStatus).isEqualTo(503)
        assertThat(UnsupportedImportFormatException("pdf").code.httpStatus).isEqualTo(415)
        assertThat(FileTooLargeException("big").code.httpStatus).isEqualTo(413)
        assertThat(UnauthenticatedException("who").code.httpStatus).isEqualTo(401)
    }
}
