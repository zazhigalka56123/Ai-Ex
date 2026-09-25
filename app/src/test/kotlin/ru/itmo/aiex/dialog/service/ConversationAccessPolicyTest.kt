package ru.itmo.aiex.dialog.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.entity.MessageScope
import java.util.UUID

class ConversationAccessPolicyTest {
    private val owner = UUID.randomUUID()

    private fun actor(vararg roles: RoleCode, id: UUID = UUID.randomUUID()) = Actor(id, roles.toSet())

    private fun decide(actor: Actor, shared: Boolean = false): ConversationAccess {
        var asked = false
        val access =
            ConversationAccessPolicy.decide(owner, actor) {
                asked = true
                shared
            }
        if (!actor.isSpecialist || actor.userId == owner) assertThat(asked).describedAs("care спрашиваем только про специалиста").isFalse()
        return access
    }

    @Test
    fun `владелец видит всё`() {
        assertThat(decide(actor(RoleCode.USER, id = owner))).isEqualTo(ConversationAccess.Granted(MessageScope.ALL))
    }

    @Test
    fun `специалист с расшаренной беседой видит всё, без расшаривания - 403`() {
        assertThat(decide(actor(RoleCode.SPECIALIST), shared = true)).isEqualTo(ConversationAccess.Granted(MessageScope.ALL))
        assertThat(decide(actor(RoleCode.SPECIALIST), shared = false)).isEqualTo(ConversationAccess.Forbidden)
    }

    @Test
    fun `администратор видит только флагнутые, даже если он ещё и специалист без расшаривания`() {
        assertThat(decide(actor(RoleCode.ADMIN, RoleCode.USER))).isEqualTo(ConversationAccess.Granted(MessageScope.FLAGGED_ONLY))
        assertThat(decide(actor(RoleCode.ADMIN, RoleCode.SPECIALIST))).isEqualTo(ConversationAccess.Granted(MessageScope.FLAGGED_ONLY))
        assertThat(decide(actor(RoleCode.ADMIN, RoleCode.SPECIALIST), shared = true)).isEqualTo(ConversationAccess.Granted(MessageScope.ALL))
    }

    @Test
    fun `посторонний пользователь - 404`() {
        assertThat(decide(actor(RoleCode.USER))).isEqualTo(ConversationAccess.Hidden)
    }
}
