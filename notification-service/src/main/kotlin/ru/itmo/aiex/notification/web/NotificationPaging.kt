package ru.itmo.aiex.notification.web

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.util.UriComponentsBuilder
import ru.itmo.aiex.common.error.FieldViolation
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.common.paging.SortOrder

object NotificationPaging {
    fun parse(request: ServerHttpRequest): PageQuery {
        val errors = mutableListOf<FieldViolation>()
        fun number(name: String, default: Int): Int {
            val raw = request.queryParams.getFirst(name)
            if (raw.isNullOrBlank()) return default
            return raw.trim().toIntOrNull() ?: default.also {
                errors += FieldViolation(name, "$name.type", "$name должен быть целым числом")
            }
        }
        val page = number("page", 0)
        val size = number("size", PageQuery.DEFAULT_SIZE)
        if (page < 0) errors += FieldViolation("page", "page.min", "page должен быть ≥ 0")
        if (size !in 1..PageQuery.MAX_SIZE) {
            errors += FieldViolation("size", "size.range", "size должен быть в диапазоне 1..${PageQuery.MAX_SIZE}")
        }
        val rawSort = request.queryParams["sort"]?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: listOf("createdAt,desc")
        val sort = rawSort.mapNotNull { parseSort(it, errors) }
        if (errors.isNotEmpty()) throw ValidationException(errors)
        return PageQuery(page, size, sort)
    }

    private fun parseSort(raw: String, errors: MutableList<FieldViolation>): SortOrder? {
        val parts = raw.split(',').map(String::trim)
        val direction = when (parts.getOrNull(1)?.lowercase()) {
            null, "asc" -> SortDirection.ASC
            "desc" -> SortDirection.DESC
            else -> null
        }
        return when {
            parts[0] != "createdAt" -> {
                errors += FieldViolation("sort", "sort.property", "Допустима сортировка только по createdAt")
                null
            }

            direction == null || parts.size > 2 -> {
                errors += FieldViolation("sort", "sort.direction", "Формат сортировки: поле,asc|desc")
                null
            }

            else -> SortOrder("createdAt", direction)
        }
    }

    fun <T> response(page: PageView<T>, request: ServerHttpRequest): ResponseEntity<List<T>> {
        val headers = HttpHeaders()
        headers.set("X-Total-Count", page.totalElements.toString())
        headers.set("X-Total-Pages", page.totalPages.toString())
        headers.set("X-Page", page.page.toString())
        headers.set("X-Page-Size", page.size.toString())
        val links = mutableListOf<String>()
        fun link(number: Int, relation: String) {
            val uri = UriComponentsBuilder.fromUri(request.uri)
                .replaceQueryParam("page", number)
                .replaceQueryParam("size", page.size)
                .build().toUriString()
            links += "<$uri>; rel=\"$relation\""
        }
        if (page.totalPages > 0) link(0, "first")
        if (page.hasPrevious) link(page.page - 1, "prev")
        if (page.hasNext) link(page.page + 1, "next")
        if (page.totalPages > 0) link(page.totalPages - 1, "last")
        if (links.isNotEmpty()) headers.set(HttpHeaders.LINK, links.joinToString(", "))
        return ResponseEntity.ok().headers(headers).body(page.items)
    }
}
