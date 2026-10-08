package ru.itmo.aiex.remote

import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class InternalTokenFilterTest {
    private val filter = InternalTokenFilter("test-token")

    @Test
    fun `внутренний endpoint отклоняет отсутствующий и неверный токен`() {
        listOf(null, "wrong-token").forEach { token ->
            val request = request("/internal/personas/test", token)
            val response = MockHttpServletResponse()
            var called = false

            filter.doFilter(request, response, FilterChain { _, _ -> called = true })

            assertThat(response.status).isEqualTo(403)
            assertThat(called).isFalse()
        }
    }

    @Test
    fun `правильный токен пропускает внутренний запрос`() {
        var called = false

        filter.doFilter(request("/internal/personas/test", "test-token"), MockHttpServletResponse(), FilterChain { _, _ -> called = true })

        assertThat(called).isTrue()
    }

    @Test
    fun `публичный endpoint не требует внутреннего токена`() {
        var called = false

        filter.doFilter(request("/api/v1/personas", null), MockHttpServletResponse(), FilterChain { _, _ -> called = true })

        assertThat(called).isTrue()
    }

    private fun request(path: String, token: String?): MockHttpServletRequest = MockHttpServletRequest("GET", path).apply {
        token?.let { addHeader(InternalApi.TOKEN_HEADER, it) }
    }
}
