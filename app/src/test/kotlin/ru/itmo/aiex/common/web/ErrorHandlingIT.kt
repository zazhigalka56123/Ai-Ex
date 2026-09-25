package ru.itmo.aiex.common.web

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import ru.itmo.aiex.testing.AbstractIntegrationTest
class ErrorHandlingIT : AbstractIntegrationTest() {
    private fun expectProblem(path: String, status: Int, code: String) = mockMvc.get("/api/v1/__test/$path").andExpect {
        status { isEqualTo(status) }
        content { contentType("application/problem+json") }
        jsonPath("$.code") { value(code) }
        jsonPath("$.status") { value(status) }
        jsonPath("$.type") { value(containsString("https://ai-ex.itmo.ru/errors/")) }
        jsonPath("$.instance") { value("/api/v1/__test/$path") }
        jsonPath("$.timestamp") { exists() }
    }

    @Test
    fun `исключения инфраструктуры переводятся в коды ошибок из общего каталога`() {
        expectProblem("integrity", 409, "CONSTRAINT_VIOLATED").andExpect { jsonPath("$.detail") { value(containsString("uq_sample")) } }
        expectProblem("optimistic", 409, "CONCURRENT_MODIFICATION")
        expectProblem("entity", 404, "NOT_FOUND")
        expectProblem("llm", 503, "LLM_UNAVAILABLE")
        expectProblem("upload", 413, "FILE_TOO_LARGE")
    }

    @Test
    fun `валидация Bean Validation и ручная валидация - 400 со списком полей`() {
        expectProblem("constraint", 400, "VALIDATION_FAILED").andExpect { jsonPath("$.errors[0].field") { value("name") } }
        expectProblem("validation", 400, "VALIDATION_FAILED").andExpect { jsonPath("$.errors[0].code") { value("cursor.invalid") } }
        mockMvc.get("/api/v1/__test/typed?n=abc").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("n") }
        }
        mockMvc.get("/api/v1/__test/typed").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].code") { value("required") }
        }
        mockMvc.get("/api/v1/__test/method-validation?n=0").andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("n") }
            jsonPath("$.errors[0].code") { value("range") }
        }
    }

    @Test
    fun `тело запроса - пустые, битые и неверного типа поля`() {
        mockMvc
            .post("/api/v1/__test/body") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"","count":1}"""
            }.andExpect { jsonPath("$.errors[0].field") { value("name") } }
        mockMvc
            .post("/api/v1/__test/body") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"x","count":"много"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.errors[0].field") { value("count") }
            }
        mockMvc
            .post("/api/v1/__test/body") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": """
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
            }
    }

    @Test
    fun `необработанная ошибка - 500 без стектрейса, но с traceId из заголовка`() {
        val result =
            mockMvc
                .get("/api/v1/__test/boom") { header("X-Trace-Id", "trace-abc-123") }
                .andExpect {
                    status { isInternalServerError() }
                    jsonPath("$.code") { value("INTERNAL_ERROR") }
                    jsonPath("$.traceId") { value("trace-abc-123") }
                    content { string(not(containsString("секретный"))) }
                    content { string(not(containsString("IllegalStateException"))) }
                }.andReturn()
        assertThat(result.response.getHeader("X-Trace-Id")).isEqualTo("trace-abc-123")
    }

    @Test
    fun `traceId генерируется, если клиент его не прислал или прислал мусор`() {
        val result = mockMvc.get("/api/v1/__test/entity") { header("X-Trace-Id", "no") }.andReturn()
        val traceId = result.response.getHeader("X-Trace-Id")
        assertThat(traceId).matches("[0-9a-f]{16}")
        assertThat(result.json()["traceId"].asString()).isEqualTo(traceId)
    }

    @Test
    fun `ошибки маршрутизации Spring MVC - в том же формате`() {
        mockMvc.get("/api/v1/nowhere").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("NOT_FOUND") }
        }
        mockMvc.delete("/api/v1/__test/typed").andExpect {
            status { isMethodNotAllowed() }
            jsonPath("$.code") { value("METHOD_NOT_ALLOWED") }
        }
        mockMvc
            .post("/api/v1/__test/body") {
                contentType = MediaType.TEXT_PLAIN
                content = "hello"
            }.andExpect {
                status { isUnsupportedMediaType() }
                jsonPath("$.code") { value("UNSUPPORTED_MEDIA_TYPE") }
            }
    }

    @Test
    fun `health-эндпоинт для HEALTHCHECK в Dockerfile`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }
}
