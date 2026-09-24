package ru.itmo.aiex.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.common.paging.SortOrder
import ru.itmo.aiex.persistence.toPageView
import ru.itmo.aiex.persistence.toPageable

class ResponsesTest {
    @BeforeEach
    fun bindRequest() {
        val request = MockHttpServletRequest("GET", "/api/v1/personas").apply { queryString = "page=1&size=2&sort=name,asc" }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @AfterEach
    fun unbindRequest() = RequestContextHolder.resetRequestAttributes()

    @Test
    fun `страница - массив в теле и метаданные в хедерах`() {
        val response = Responses.page(PageView(listOf("b", "c"), page = 1, size = 2, totalElements = 5))
        assertThat(response.body).containsExactly("b", "c")
        assertThat(response.headers[AiExHeaders.TOTAL_COUNT]).containsExactly("5")
        assertThat(response.headers[AiExHeaders.TOTAL_PAGES]).containsExactly("3")
        assertThat(response.headers[AiExHeaders.PAGE]).containsExactly("1")
        val link = response.headers.getFirst(HttpHeaders.LINK)!!
        val rels = link.split(", ").associate { part -> part.substringAfter("rel=\"").removeSuffix("\"") to part.substringBefore(">;") }
        assertThat(rels.keys).containsExactly("first", "prev", "next", "last")
        assertThat(rels["first"]).contains("sort=name,asc", "page=0", "size=2")
        assertThat(rels["next"]).contains("page=2")
        assertThat(rels["last"]).contains("page=2")
    }

    @Test
    fun `пустая страница - без Link`() {
        val response = Responses.page(PageView<String>(emptyList(), page = 0, size = 20, totalElements = 0))
        assertThat(response.headers[AiExHeaders.TOTAL_COUNT]).containsExactly("0")
        assertThat(response.headers.containsHeader(HttpHeaders.LINK)).isFalse()
    }

    @Test
    fun `201 и 202 с Location`() {
        val created = Responses.created("body", "/api/v1/personas/{id}", 42)
        assertThat(created.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(created.headers.location.toString()).endsWith("/api/v1/personas/42")
        val accepted = Responses.accepted("body", "/api/v1/imports/{id}", "abc")
        assertThat(accepted.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(accepted.headers.location.toString()).endsWith("/api/v1/imports/abc")
    }

    @Test
    fun `перевод пагинации в Spring Data и обратно`() {
        val pageable =
            PageQuery(2, 10, listOf(SortOrder("createdAt", SortDirection.DESC), SortOrder("name", SortDirection.ASC)))
                .toPageable(mapOf("name" to "displayName"))
        assertThat(pageable.pageNumber).isEqualTo(2)
        assertThat(pageable.sort).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("displayName"))
        val view = PageImpl(listOf("x"), PageRequest.of(2, 10), 21).toPageView()
        assertThat(view).isEqualTo(PageView(listOf("x"), 2, 10, 21))
    }
}
