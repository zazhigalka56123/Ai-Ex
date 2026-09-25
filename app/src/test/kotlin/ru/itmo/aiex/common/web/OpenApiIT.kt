package ru.itmo.aiex.common.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import ru.itmo.aiex.testing.AbstractIntegrationTest
class OpenApiIT : AbstractIntegrationTest() {
    private val groups = listOf("all", "iam", "persona", "ingest", "dialog", "care", "admin", "notification")

    @Test
    fun `каждая операция документирована полностью`() {
        val spec = mockMvc.get("/v3/api-docs/all").andExpect { status { isOk() } }.andReturn().json()
        assertThat(spec["components"]["schemas"].has("Problem")).isTrue()

        val operations =
            spec["paths"].properties().flatMap { (path, item) ->
                item.properties().map { (method, operation) -> Triple(path, method, operation) }
            }.filterNot { (path, _, _) -> path.startsWith("/api/v1/__test") }
        assertThat(operations).isNotEmpty()
        operations.forEach { (path, method, operation) ->
            val where = "${method.uppercase()} $path"
            assertThat(operation.path("operationId").asString()).describedAs("operationId у $where").matches("[a-z][A-Za-z]+")
            assertThat(operation.path("summary").asString()).describedAs("summary у $where").isNotBlank()
            val codes = operation["responses"].propertyNames().toSet()
            assertThat(codes).describedAs("ответы у $where").contains("500")
            assertThat(codes.any { it.startsWith("2") }).describedAs("успешный ответ у $where").isTrue()
        }
        val operationIds = operations.map { it.third["operationId"].asString() }
        assertThat(operationIds).describedAs("operationId уникальны").doesNotHaveDuplicates()
    }

    @Test
    fun `группы модулей и Swagger UI доступны`() {
        groups.forEach { group -> mockMvc.get("/v3/api-docs/$group").andExpect { status { isOk() } } }
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }
}
