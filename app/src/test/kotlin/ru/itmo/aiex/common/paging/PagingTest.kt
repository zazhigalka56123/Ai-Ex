package ru.itmo.aiex.common.paging

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PagingTest {
    @Test
    fun `арифметика страниц`() {
        val page = PageView(listOf(1, 2), page = 1, size = 2, totalElements = 5)
        assertThat(page.totalPages).isEqualTo(3)
        assertThat(page.hasNext).isTrue()
        assertThat(page.hasPrevious).isTrue()
        assertThat(page.map { it * 10 }.items).containsExactly(10, 20)
        assertThat(PageView<Int>(emptyList(), 0, 20, 0).totalPages).isZero()
        assertThat(PageQuery(page = 3, size = 20).offset).isEqualTo(60)
    }

    @Test
    fun `инварианты запроса страницы`() {
        assertThatThrownBy { PageQuery(page = -1, size = 10) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { PageQuery(page = 0, size = 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { CursorQuery(cursor = null, limit = 0) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `лишняя запись в выборке означает следующую страницу`() {
        val page = CursorPage.fromOverfetch(listOf(1, 2, 3, 4), limit = 3) { "c$it" }
        assertThat(page.items).containsExactly(1, 2, 3)
        assertThat(page.nextCursor).isEqualTo("c3")

        val last = CursorPage.fromOverfetch(listOf(1, 2), limit = 3) { "c$it" }
        assertThat(last.items).containsExactly(1, 2)
        assertThat(last.nextCursor).isNull()
        assertThat(last.map { it.toString() }.items).containsExactly("1", "2")
    }
}
