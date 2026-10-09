package ru.itmo.aiex.reactive

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.util.UriComponentsBuilder
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.web.AiExHeaders

/** Ответ со страницей: элементы в теле, общее количество и навигация - в заголовках, как в Spring MVC-сервисах. */
object ReactiveResponses {
    fun <T : Any> page(page: PageView<T>, request: ServerHttpRequest): ResponseEntity<List<T>> {
        val headers = HttpHeaders()
        headers.set(AiExHeaders.TOTAL_COUNT, page.totalElements.toString())
        headers.set(AiExHeaders.TOTAL_PAGES, page.totalPages.toString())
        headers.set(AiExHeaders.PAGE, page.page.toString())
        headers.set(AiExHeaders.PAGE_SIZE, page.size.toString())
        linkHeader(page, request)?.let { headers.set(HttpHeaders.LINK, it) }
        return ResponseEntity.ok().headers(headers).body(page.items)
    }

    private fun linkHeader(page: PageView<*>, request: ServerHttpRequest): String? {
        val links = mutableListOf<String>()
        fun link(number: Int, rel: String) {
            val uri =
                UriComponentsBuilder
                    .fromUri(request.uri)
                    .replaceQueryParam("page", number)
                    .replaceQueryParam("size", page.size)
                    .build()
                    .toUriString()
            links += "<$uri>; rel=\"$rel\""
        }
        if (page.totalPages > 0) link(0, "first")
        if (page.hasPrevious) link(page.page - 1, "prev")
        if (page.hasNext) link(page.page + 1, "next")
        if (page.totalPages > 0) link(page.totalPages - 1, "last")
        return links.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}
