package ru.itmo.aiex.web

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import ru.itmo.aiex.common.error.UnauthenticatedException
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.web.SampleController.Companion.parameter
import java.util.UUID

class CurrentActorArgumentResolverTest {
    private val known = Actor(UUID.randomUUID(), setOf(RoleCode.USER))
    private val resolver = CurrentActorArgumentResolver { id -> known.takeIf { it.userId == id } }

    private fun request(header: String?) =
        ServletWebRequest(MockHttpServletRequest().apply { if (header != null) addHeader(AiExHeaders.USER_ID, header) })

    @Test
    fun `известный пользователь становится актором`() {
        assertThat(resolver.supportsParameter(parameter("actor"))).isTrue()
        assertThat(resolver.supportsParameter(parameter("anonymous"))).isFalse()
        assertThat(resolver.resolveArgument(parameter("actor"), null, request(known.userId.toString()), null)).isEqualTo(known)
    }

    @Test
    fun `без заголовка - 401, но для необязательного актора - null`() {
        assertThatThrownBy { resolver.resolveArgument(parameter("actor"), null, request(null), null) }
            .isInstanceOf(UnauthenticatedException::class.java)
        assertThat(resolver.resolveArgument(parameter("optionalActor"), null, request("  "), null)).isNull()
    }

    @Test
    fun `не UUID и неизвестный пользователь - 401`() {
        assertThatThrownBy { resolver.resolveArgument(parameter("actor"), null, request("nope"), null) }
            .isInstanceOf(UnauthenticatedException::class.java)
            .hasMessageContaining("UUID")
        assertThatThrownBy { resolver.resolveArgument(parameter("actor"), null, request(UUID.randomUUID().toString()), null) }
            .isInstanceOf(UnauthenticatedException::class.java)
            .hasMessageContaining("не найден")
    }
}
