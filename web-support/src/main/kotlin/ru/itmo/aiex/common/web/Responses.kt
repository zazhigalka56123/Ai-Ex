package ru.itmo.aiex.common.web

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import ru.itmo.aiex.common.paging.PageView
import java.net.URI

object Responses {
    fun <T : Any> created(body: T, pathTemplate: String, vararg uriVariables: Any): ResponseEntity<T> =
        ResponseEntity.created(location(pathTemplate, *uriVariables)).body(body)

    fun <T : Any> accepted(body: T, pathTemplate: String, vararg uriVariables: Any): ResponseEntity<T> = ResponseEntity
        .accepted()
        .location(location(pathTemplate, *uriVariables))
        .body(body)

    fun <T> page(page: PageView<T>): ResponseEntity<List<T>> {
        val headers = HttpHeaders()
        headers.set(AiExHeaders.TOTAL_COUNT, page.totalElements.toString())
        headers.set(AiExHeaders.TOTAL_PAGES, page.totalPages.toString())
        headers.set(AiExHeaders.PAGE, page.page.toString())
        headers.set(AiExHeaders.PAGE_SIZE, page.size.toString())
        linkHeader(page)?.let { headers.set(HttpHeaders.LINK, it) }
        return ResponseEntity.ok().headers(headers).body(page.items)
    }

    private fun linkHeader(page: PageView<*>): String? {
        val links = mutableListOf<String>()
        fun link(number: Int, rel: String) {
            val uri =
                ServletUriComponentsBuilder
                    .fromCurrentRequest()
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

    private fun location(pathTemplate: String, vararg uriVariables: Any): URI = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path(pathTemplate)
        .buildAndExpand(*uriVariables)
        .toUri()
}
