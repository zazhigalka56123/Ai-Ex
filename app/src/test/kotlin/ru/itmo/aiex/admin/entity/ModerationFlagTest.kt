package ru.itmo.aiex.admin.entity

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import ru.itmo.aiex.admin.entity.FlagStatus.IN_REVIEW
import ru.itmo.aiex.admin.entity.FlagStatus.OPEN
import ru.itmo.aiex.admin.entity.FlagStatus.REJECTED
import ru.itmo.aiex.admin.entity.FlagStatus.RESOLVED
import ru.itmo.aiex.admin.service.FlaggedMessage
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import ru.itmo.aiex.common.moderation.FlagReason
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream

class ModerationFlagTest {
    private val now = Instant.parse("2026-10-01T09:00:00Z")
    private val message = FlaggedMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

    private fun flagIn(status: FlagStatus): ModerationFlag {
        val flag = ModerationFlag.reportedBy(UUID.randomUUID(), message, UUID.randomUUID(), FlagReason.ABUSE, null, now)
        when (status) {
            OPEN -> Unit
            IN_REVIEW -> flag.review(IN_REVIEW, null, UUID.randomUUID(), now)
            RESOLVED, REJECTED -> flag.review(status, null, UUID.randomUUID(), now)
        }
        return flag
    }

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @MethodSource("allTransitions")
    fun `каждая комбинация перехода статуса флага`(from: FlagStatus, to: FlagStatus, allowed: Boolean) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed)
        val flag = flagIn(from)
        if (allowed) {
            flag.review(to, null, UUID.randomUUID(), now)
            assertThat(flag.status).isEqualTo(to)
        } else {
            assertThatThrownBy { flag.review(to, null, UUID.randomUUID(), now) }
                .isInstanceOf(IllegalStateTransitionException::class.java)
                .extracting { (it as IllegalStateTransitionException).code }
                .isEqualTo(ErrorCode.FLAG_INVALID_STATE)
            assertThat(flag.status).isEqualTo(from)
        }
    }

    @Test
    fun `вердикт назначает модератора, терминальный - фиксирует время разбора`() {
        val reviewer = UUID.randomUUID()
        val flag = flagIn(OPEN)
        val later = now.plusSeconds(60)
        flag.review(IN_REVIEW, null, reviewer, later)
        assertThat(flag.assigneeId).isEqualTo(reviewer)
        assertThat(flag.updatedAt).isEqualTo(later)
        assertThat(flag.resolvedAt).isNull()

        flag.review(REJECTED, "Жалоба не подтвердилась", reviewer, later.plusSeconds(1))
        assertThat(flag.resolvedAt).isEqualTo(later.plusSeconds(1))
        assertThat(flag.resolution).isEqualTo("Жалоба не подтвердилась")
        assertThat(REJECTED.isTerminal && RESOLVED.isTerminal && !IN_REVIEW.isTerminal && !OPEN.isTerminal).isTrue()
    }

    @Test
    fun `жалоба человека - с автором, флаг guardrails - без автора, с усечённым пояснением`() {
        val reporter = UUID.randomUUID()
        val human = ModerationFlag.reportedBy(UUID.randomUUID(), message, reporter, FlagReason.SPAM, "спам", now)
        assertThat(human.source).isEqualTo(FlagSource.USER)
        assertThat(human.reporterId).isEqualTo(reporter)
        assertThat(human.personaId).isEqualTo(message.personaId)
        assertThat(human.status).isEqualTo(OPEN)

        val system = ModerationFlag.raisedByGuardrail(UUID.randomUUID(), message, FlagReason.SELF_HARM, "x".repeat(600), now)
        assertThat(system.source).isEqualTo(FlagSource.GUARDRAIL)
        assertThat(system.reporterId).isNull()
        assertThat(system.comment).hasSize(ModerationFlag.COMMENT_MAX_LENGTH)
        assertThat(ModerationFlag.raisedByGuardrail(UUID.randomUUID(), message, FlagReason.ABUSE, "  ", now).comment).isNull()

        assertThat(human).isEqualTo(human).isNotEqualTo(system)
        assertThat(human.hashCode()).isEqualTo(human.id.hashCode())
    }

    companion object {
        private val allowed =
            setOf(OPEN to IN_REVIEW, OPEN to RESOLVED, OPEN to REJECTED, IN_REVIEW to RESOLVED, IN_REVIEW to REJECTED)

        @JvmStatic
        fun allTransitions(): Stream<Arguments> = FlagStatus.entries
            .flatMap { from -> FlagStatus.entries.map { to -> Arguments.of(from, to, (from to to) in allowed) } }
            .stream()
    }
}
