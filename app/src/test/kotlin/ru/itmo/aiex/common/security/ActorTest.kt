package ru.itmo.aiex.common.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import java.util.UUID

class ActorTest {
    private val specialist = Actor(UUID.randomUUID(), setOf(RoleCode.SPECIALIST))

    @Test
    fun `проверки ролей`() {
        assertThat(specialist.isSpecialist).isTrue()
        assertThat(specialist.isAdmin).isFalse()
        specialist.requireRole(RoleCode.SPECIALIST)
        specialist.requireAnyRole(RoleCode.ADMIN, RoleCode.SPECIALIST)
    }

    @Test
    fun `чужая роль - 403`() {
        assertThatThrownBy { specialist.requireRole(RoleCode.ADMIN) }
            .isInstanceOf(ForbiddenException::class.java)
            .extracting("code")
            .isEqualTo(ErrorCode.FORBIDDEN)
        assertThatThrownBy { specialist.requireAnyRole(RoleCode.ADMIN, RoleCode.USER) }.isInstanceOf(ForbiddenException::class.java)
    }
}
