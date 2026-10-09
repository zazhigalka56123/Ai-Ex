package ru.itmo.aiex.common.web

import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.common.paging.SortOrder

/** Разбор `page`/`size`/`sort` без привязки к стеку: одни правила для Spring MVC и WebFlux. */
class PageQueryParser(val defaultSize: Int = PageQuery.DEFAULT_SIZE, val maxSize: Int = PageQuery.MAX_SIZE) {
    init {
        require(maxSize in 1..PageQuery.MAX_SIZE) { "aiex.pagination.max-size должен быть в диапазоне 1..${PageQuery.MAX_SIZE}" }
        require(defaultSize in 1..maxSize) { "aiex.pagination.default-size должен быть в диапазоне 1..$maxSize" }
    }

    fun parse(rawPage: String?, rawSize: String?, rawSorts: List<String>?, settings: PageParams?): PageQuery {
        val violations = mutableListOf<FieldViolation>()

        val page = parseInt(rawPage, "page", 0, violations)
        if (page != null && page < 0) violations += FieldViolation("page", "page.min", "page должен быть ≥ 0")

        val size = parseInt(rawSize, "size", defaultSize, violations)
        if (size != null && size !in 1..maxSize) {
            violations += FieldViolation("size", "size.range", "size должен быть в диапазоне 1..$maxSize")
        }

        val sortable = settings?.sortable?.toSet().orEmpty()
        val sorts =
            rawSorts?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(settings?.defaultSort?.takeIf { it.isNotBlank() })
        val sort = sorts.mapNotNull { parseSort(it, sortable, violations) }

        if (violations.isNotEmpty()) throw ValidationException(violations)
        return PageQuery(page = page ?: 0, size = size ?: defaultSize, sort = sort)
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
