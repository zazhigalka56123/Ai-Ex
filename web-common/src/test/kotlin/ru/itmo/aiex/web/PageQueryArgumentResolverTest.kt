package ru.itmo.aiex.web

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.SortDirection
import ru.itmo.aiex.common.paging.SortOrder
import ru.itmo.aiex.web.SampleController.Companion.parameter

class PageQueryArgumentResolverTest {
    private val properties = PaginationProperties()
    private val resolver = PageQueryArgumentResolver(properties)
    private val cursorResolver = CursorQueryArgumentResolver(properties)

    private fun request(vararg params: Pair<String, String>) =
        ServletWebRequest(MockHttpServletRequest().apply { params.forEach { (k, v) -> addParameter(k, v) } })

    private fun resolve(method: String, vararg params: Pair<String, String>) =
        resolver.resolveArgument(parameter(method), null, request(*params), null)

    private fun violationsOf(block: () -> Unit): List<String> = try {
        block()
        emptyList()
    } catch (ex: ValidationException) {
        ex.violations.map { it.field }
    }

    @Test
    fun `значения по умолчанию и сортировка по умолчанию`() {
        assertThat(resolver.supportsParameter(parameter("paged"))).isTrue()
        assertThat(resolver.supportsParameter(parameter("actor"))).isFalse()
        assertThat(resolve("paged")).isEqualTo(PageQuery(0, 20, listOf(SortOrder("createdAt", SortDirection.DESC))))
    }

    @Test
    fun `явные page, size и несколько полей сортировки`() {
        val request =
            ServletWebRequest(
                MockHttpServletRequest().apply {
                    addParameter("page", "2")
                    addParameter("size", "50")
                    addParameter("sort", "name,asc", "createdAt,DESC")
                },
            )
        val query = resolver.resolveArgument(parameter("paged"), null, request, null)
        assertThat(query.page).isEqualTo(2)
        assertThat(query.size).isEqualTo(50)
        assertThat(query.sort).containsExactly(SortOrder("name", SortDirection.ASC), SortOrder("createdAt", SortDirection.DESC))
        assertThat(resolve("paged", "sort" to "name").sort).containsExactly(SortOrder("name", SortDirection.ASC))
    }

    @Test
    fun `превышение потолка, отрицательная страница и мусор - 400 со всеми полями сразу`() {
        assertThat(violationsOf { resolve("paged", "size" to "51", "page" to "-1") }).containsExactlyInAnyOrder("size", "page")
        assertThat(violationsOf { resolve("paged", "size" to "abc") }).containsExactly("size")
        assertThat(violationsOf { resolve("paged", "size" to "0") }).containsExactly("size")
    }

    @Test
    fun `сортировка только по whitelist`() {
        assertThat(violationsOf { resolve("paged", "sort" to "password,asc") }).containsExactly("sort")
        assertThat(violationsOf { resolve("paged", "sort" to "name,sideways") }).containsExactly("sort")
        assertThat(violationsOf { resolve("paged", "sort" to "name,asc,extra") }).containsExactly("sort")
        assertThat(violationsOf { resolve("pagedWithoutSort", "sort" to "name") }).containsExactly("sort")
        assertThat(resolve("pagedWithoutSort").sort).isEmpty()
    }

    @Test
    fun `курсорная пагинация - лимит по умолчанию из аннотации и из настроек`() {
        assertThat(cursorResolver.supportsParameter(parameter("cursor"))).isTrue()
        assertThat(cursorResolver.resolveArgument(parameter("cursor"), null, request(), null)).isEqualTo(CursorQuery(null, 10))
        assertThat(cursorResolver.resolveArgument(parameter("cursorDefault"), null, request(), null)).isEqualTo(CursorQuery(null, 30))
        assertThat(cursorResolver.resolveArgument(parameter("cursor"), null, request("cursor" to " abc ", "limit" to "5"), null))
            .isEqualTo(CursorQuery("abc", 5))
    }

    @Test
    fun `курсорная пагинация - лимит больше потолка`() {
        assertThatThrownBy { cursorResolver.resolveArgument(parameter("cursor"), null, request("limit" to "500"), null) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `настройки пагинации проверяются при старте`() {
        assertThatThrownBy { PaginationProperties(maxSize = 100) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PaginationProperties(defaultSize = 60) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PaginationProperties(defaultCursorLimit = 0) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
