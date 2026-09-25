package ru.itmo.aiex.common.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.get
import ru.itmo.aiex.testing.AbstractIntegrationTest
import tools.jackson.databind.JsonNode
import java.nio.file.Files
import java.nio.file.Path

class OpenApiSnapshotIT : AbstractIntegrationTest() {
    private val snapshot: Path = Path.of(System.getProperty("aiex.rootDir", "..")).resolve("docs/openapi/operations.snapshot.json")

    @Test
    fun `статусы и коды ошибок API не меняются незаметно`() {
        val spec = mockMvc.get("/v3/api-docs/all").andReturn().json()
        val actual = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summarize(spec)) + "\n"

        if (System.getProperty("openapi.snapshot.update") == "true") {
            Files.createDirectories(snapshot.parent)
            Files.writeString(snapshot, actual)
        }
        assertThat(Files.exists(snapshot))
            .describedAs("Нет снапшота $snapshot - сгенерируйте его с -Dopenapi.snapshot.update=true")
            .isTrue()
        assertThat(actual)
            .describedAs("Контракт API изменился. Если это осознанно - обновите снапшот с -Dopenapi.snapshot.update=true")
            .isEqualTo(Files.readString(snapshot))
    }

    private fun summarize(spec: JsonNode): Map<String, Any> = spec["paths"]
        .properties()
        .filterNot { (path, _) -> path.startsWith("/api/v1/__test") }
        .flatMap { (path, item) ->
            item.properties().map { (method, operation) ->
                val responses =
                    operation["responses"].properties().associate { (status, response) ->
                        val examples = response.path("content").path("application/problem+json").path("examples")
                        status to examples.propertyNames().sorted()
                    }
                operation["operationId"].asString() to
                    linkedMapOf("method" to method.uppercase(), "path" to path, "responses" to responses.toSortedMap())
            }
        }.sortedBy { it.first }
        .toMap(LinkedHashMap())
}
