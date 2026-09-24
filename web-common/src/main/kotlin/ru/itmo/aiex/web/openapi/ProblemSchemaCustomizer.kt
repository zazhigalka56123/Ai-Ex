package ru.itmo.aiex.web.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.DateTimeSchema
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import ru.itmo.aiex.common.error.ErrorCode

class ProblemSchemaCustomizer : GlobalOpenApiCustomizer {
    override fun customise(openApi: OpenAPI) {
        val components = openApi.components ?: Components().also { openApi.components = it }
        components.addSchemas(
            FIELD_VIOLATION,
            ObjectSchema()
                .description("Ошибка валидации одного поля")
                .addProperty("field", StringSchema().example("personaId"))
                .addProperty("code", StringSchema().example("state.invalid"))
                .addProperty("message", StringSchema().example("ожидался статус READY, фактический TRAINING")),
        )
        components.addSchemas(
            PROBLEM,
            ObjectSchema()
                .description("Ошибка в формате RFC 9457 ProblemDetail")
                .addProperty("type", StringSchema().format("uri"))
                .addProperty("title", StringSchema())
                .addProperty("status", IntegerSchema())
                .addProperty("detail", StringSchema())
                .addProperty("instance", StringSchema())
                .addProperty("code", StringSchema().apply { setEnum(ErrorCode.entries.map { it.name }) })
                .addProperty("traceId", StringSchema())
                .addProperty("timestamp", DateTimeSchema())
                .addProperty("errors", ArraySchema().items(Schema<Any>().`$ref`("#/components/schemas/$FIELD_VIOLATION"))),
        )
    }

    companion object {
        const val PROBLEM = "Problem"
        const val FIELD_VIOLATION = "FieldViolation"
        const val PROBLEM_REF = "#/components/schemas/$PROBLEM"
    }
}
