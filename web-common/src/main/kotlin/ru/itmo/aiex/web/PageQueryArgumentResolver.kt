package ru.itmo.aiex.web

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.common.paging.SortOrder

class PageQueryArgumentResolver(private val properties: PaginationProperties) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == PageQuery::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): PageQuery {
        val settings = parameter.getParameterAnnotation(PageParams::class.java)
        val violations = mutableListOf<FieldViolation>()

        val page = parseInt(webRequest.getParameter("page"), "page", 0, violations)
        if (page != null && page < 0) violations += FieldViolation("page", "page.min", "page должен быть ≥ 0")

        val size = parseInt(webRequest.getParameter("size"), "size", properties.defaultSize, violations)
        if (size != null && size !in 1..properties.maxSize) {
            violations += FieldViolation("size", "size.range", "size должен быть в диапазоне 1..${properties.maxSize}")
        }

        val sortable = settings?.sortable?.toSet().orEmpty()
        val rawSort =
            webRequest.getParameterValues("sort")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(settings?.defaultSort?.takeIf { it.isNotBlank() })
        val sort = rawSort.mapNotNull { parseSort(it, sortable, violations) }

        if (violations.isNotEmpty()) throw ValidationException(violations)
        return PageQuery(page = page ?: 0, size = size ?: properties.defaultSize, sort = sort)
    }

    private fun parseSort(raw: String, sortable: Set<String>, violations: MutableList<FieldViolation>): SortOrder? {
        val parts = raw.split(',').map { it.trim() }
        val property = parts[0]
        val direction =
            when (parts.getOrNull(1)?.lowercase()) {
                null, "asc" -> SortDirection.ASC
                "desc" -> SortDirection.DESC
                else -> null
            }
        return when {
            property !in sortable -> {
                val allowed = if (sortable.isEmpty()) "сортировка не поддерживается" else "допустимо: ${sortable.joinToString()}"
                violations += FieldViolation("sort", "sort.property", "Нельзя сортировать по '$property' - $allowed")
                null
            }

            direction == null || parts.size > 2 -> {
                violations += FieldViolation("sort", "sort.direction", "Формат сортировки: поле,asc|desc")
                null
            }

            else -> SortOrder(property, direction)
        }
    }
}
