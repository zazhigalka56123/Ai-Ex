package ru.itmo.aiex.remote

import feign.Request
import feign.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.FieldViolation
import tools.jackson.databind.json.JsonMapper

class RemoteErrorDecoderTest {
    private val decoder = RemoteErrorDecoder(JsonMapper.builder().build())

    @Test
    fun `ошибка домена сохраняет код и нарушения полей`() {
        val response = response(
            409,
            """
            {"code":"PERSONA_NOT_READY","detail":"Персона обучается",
             "errors":[{"field":"personaId","code":"state.invalid","message":"TRAINING"}]}
            """.trimIndent(),
        )

        val error = decoder.decode("PersonaClient#assertOwned", response) as AiExException

        assertThat(error.code).isEqualTo(ErrorCode.PERSONA_NOT_READY)
        assertThat(error.message).isEqualTo("Персона обучается")
        assertThat(error.violations).containsExactly(FieldViolation("personaId", "state.invalid", "TRAINING"))
    }

    @Test
    fun `ошибка балансировщика без JSON становится 503`() {
        val error = decoder.decode("AccountClient#findActive", response(503, "Load balancer has no available server")) as AiExException

        assertThat(error.code).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE)
    }

    private fun response(status: Int, body: String): Response = Response.builder()
        .status(status)
        .reason("test")
        .headers(emptyMap())
        .request(Request.create(Request.HttpMethod.GET, "http://account-service/internal/users/test", emptyMap(), null, Charsets.UTF_8, null))
        .body(body, Charsets.UTF_8)
        .build()
}
