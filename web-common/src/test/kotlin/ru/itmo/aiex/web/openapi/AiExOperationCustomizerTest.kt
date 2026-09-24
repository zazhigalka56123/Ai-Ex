package ru.itmo.aiex.web.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.ArraySchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.aiex.web.AiExHeaders
import ru.itmo.aiex.web.PaginationProperties
import ru.itmo.aiex.web.SampleController.Companion.handler

class AiExOperationCustomizerTest {
    private val customizer = AiExOperationCustomizer(PaginationProperties())

    @Test
    fun `из сигнатуры выводятся заголовок актора, пагинация и ответы-ошибки`() {
        val operation = customizer.customize(Operation(), handler("documented"))

        val names = operation.parameters.map { it.name }
        assertThat(names).containsExactly(AiExHeaders.USER_ID, "page", "size", "sort", "cursor", "limit")
        assertThat(operation.parameters.first().required).isTrue()
        val sort = operation.parameters.first { it.name == "sort" }.schema as ArraySchema
        assertThat(sort.items.enum).containsExactly("name,asc", "name,desc")

        assertThat(operation.responses.keys).containsExactly("400", "401", "404", "409", "500")
        val conflict = operation.responses["409"]!!
        assertThat(conflict.description).contains("PERSONA_NOT_READY", "CONCURRENT_MODIFICATION")
        val media = conflict.content["application/problem+json"]!!
        assertThat(media.schema.`$ref`).isEqualTo(ProblemSchemaCustomizer.PROBLEM_REF)
        assertThat(media.examples.keys).containsExactly("PERSONA_NOT_READY", "CONCURRENT_MODIFICATION")
    }

    @Test
    fun `необязательный актор не даёт 401, операция без параметров - только 500`() {
        val optional = customizer.customize(Operation(), handler("optionalActor"))
        assertThat(optional.parameters.single().required).isFalse()
        assertThat(optional.responses.keys).containsExactly("500")

        val plain = customizer.customize(Operation(), handler("anonymous"))
        assertThat(plain.parameters).isNull()
        assertThat(plain.responses.keys).containsExactly("500")
    }

    @Test
    fun `пагинация без сортировки описана без enum`() {
        val operation = customizer.customize(Operation(), handler("pagedWithoutSort"))
        val sort = operation.parameters.first { it.name == "sort" }
        assertThat(sort.description).contains("не поддерживается")
        assertThat((sort.schema as ArraySchema).items.enum).isNull()
    }

    @Test
    fun `схема Problem регистрируется один раз на всё API`() {
        val openApi = OpenAPI()
        ProblemSchemaCustomizer().customise(openApi)
        assertThat(openApi.components.schemas.keys).contains(ProblemSchemaCustomizer.PROBLEM, ProblemSchemaCustomizer.FIELD_VIOLATION)
        assertThat(openApi.components.schemas[ProblemSchemaCustomizer.PROBLEM]!!.properties.keys)
            .contains("type", "title", "status", "detail", "instance", "code", "traceId", "timestamp", "errors")
    }
}
