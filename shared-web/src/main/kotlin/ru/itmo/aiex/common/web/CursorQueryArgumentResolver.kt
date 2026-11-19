package ru.itmo.aiex.common.web

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.CursorQuery
class CursorQueryArgumentResolver(private val properties: PaginationProperties) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == CursorQuery::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): CursorQuery {
        val settings = parameter.getParameterAnnotation(CursorParams::class.java)
        val defaultLimit = settings?.defaultLimit?.takeIf { it > 0 } ?: properties.defaultCursorLimit
        val violations = mutableListOf<FieldViolation>()
        val limit = parseInt(webRequest.getParameter("limit"), "limit", defaultLimit, violations)
        if (limit != null && limit !in 1..properties.maxSize) {
            violations += FieldViolation("limit", "limit.range", "limit должен быть в диапазоне 1..${properties.maxSize}")
        }
        if (violations.isNotEmpty()) throw ValidationException(violations)
        val cursor = webRequest.getParameter("cursor")?.trim()?.takeIf { it.isNotEmpty() }
        return CursorQuery(cursor = cursor, limit = limit ?: defaultLimit)
    }
}
