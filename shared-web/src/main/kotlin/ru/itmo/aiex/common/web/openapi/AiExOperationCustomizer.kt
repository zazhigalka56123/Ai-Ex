package ru.itmo.aiex.common.web.openapi

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.QueryParameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import org.springdoc.core.customizers.GlobalOperationCustomizer
import org.springframework.web.method.HandlerMethod
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.AiExHeaders
import ru.itmo.aiex.common.web.CursorParams
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.PaginationProperties
class AiExOperationCustomizer(private val pagination: PaginationProperties) : GlobalOperationCustomizer {
    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val errors = linkedSetOf<ErrorCode>()
        handlerMethod.methodParameters.forEach { parameter ->
            when (parameter.parameterType) {
                Actor::class.java -> {
                    val required = !parameter.isOptional
                    operation.addParametersItem(actorHeader(required))
                    if (required) errors += ErrorCode.UNAUTHENTICATED
                }

                PageQuery::class.java -> {
                    pageParameters(parameter.getParameterAnnotation(PageParams::class.java)).forEach(operation::addParametersItem)
                    errors += ErrorCode.VALIDATION_FAILED
                }

                CursorQuery::class.java -> {
                    cursorParameters(parameter.getParameterAnnotation(CursorParams::class.java)).forEach(operation::addParametersItem)
                    errors += ErrorCode.VALIDATION_FAILED
                }
            }
        }
        handlerMethod.getMethodAnnotation(ApiErrors::class.java)?.let { errors += it.value }
        errors += ErrorCode.INTERNAL_ERROR

        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
        errors.groupBy { it.httpStatus }.toSortedMap().forEach { (status, codes) ->
            responses.addApiResponse(status.toString(), problemResponse(codes))
        }
        return operation
    }

    private fun actorHeader(required: Boolean): Parameter = HeaderParameter()
        .name(AiExHeaders.USER_ID)
        .required(required)
        .description("Текущий пользователь (лаб. 1). В лаб. 3 заменится на JWT.")
        .schema(StringSchema().format("uuid"))

    private fun pageParameters(settings: PageParams?): List<Parameter> {
        val sortValues = settings?.sortable?.flatMap { listOf("$it,asc", "$it,desc") }.orEmpty()
        val sortParameter =
            QueryParameter()
                .name("sort")
                .description(
                    if (sortValues.isEmpty()) {
                        "Сортировка не поддерживается"
                    } else {
                        "Сортировка `поле,asc|desc`, по умолчанию `${settings?.defaultSort}`"
                    },
                ).schema(ArraySchema().items(StringSchema().apply { if (sortValues.isNotEmpty()) setEnum(sortValues) }))
        return listOf(
            QueryParameter().name("page").description("Номер страницы с нуля").schema(IntegerSchema().minimum(0.toBigDecimal())._default(0)),
            QueryParameter()
                .name("size")
                .description("Размер страницы, максимум ${pagination.maxSize}")
                .schema(IntegerSchema().minimum(1.toBigDecimal()).maximum(pagination.maxSize.toBigDecimal())._default(pagination.defaultSize)),
            sortParameter,
        )
    }

    private fun cursorParameters(settings: CursorParams?): List<Parameter> {
        val defaultLimit = settings?.defaultLimit?.takeIf { it > 0 } ?: pagination.defaultCursorLimit
        return listOf(
            QueryParameter().name("cursor").description("Непрозрачный курсор из `nextCursor` предыдущей порции").schema(StringSchema()),
            QueryParameter()
                .name("limit")
                .description("Размер порции, максимум ${pagination.maxSize}")
                .schema(IntegerSchema().minimum(1.toBigDecimal()).maximum(pagination.maxSize.toBigDecimal())._default(defaultLimit)),
        )
    }

    private fun problemResponse(codes: List<ErrorCode>): ApiResponse {
        val media =
            MediaType().schema(Schema<Any>().`$ref`(ProblemSchemaCustomizer.PROBLEM_REF))
        codes.forEach { code ->
            media.addExamples(
                code.name,
                Example().summary(code.title).value(
                    linkedMapOf(
                        "type" to "https://ai-ex.itmo.ru/errors/${code.slug}",
                        "title" to code.title,
                        "status" to code.httpStatus,
                        "detail" to code.title,
                        "instance" to "/api/v1/...",
                        "code" to code.name,
                        "traceId" to "0f1c2d3e4a5b6c7d",
                        "timestamp" to "2026-09-22T11:04:00Z",
                    ),
                ),
            )
        }
        return ApiResponse()
            .description(codes.joinToString("; ") { "`${it.name}` - ${it.title}" })
            .content(Content().addMediaType(PROBLEM_JSON, media))
    }

    private companion object {
        const val PROBLEM_JSON = "application/problem+json"
    }
}
