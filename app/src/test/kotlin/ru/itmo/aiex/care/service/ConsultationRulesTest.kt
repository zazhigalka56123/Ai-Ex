package ru.itmo.aiex.care.service

import ru.itmo.aiex.care.entity.ConsultationRole
import ru.itmo.aiex.care.entity.SessionStatus

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import ru.itmo.aiex.care.entity.ConsultationRole.ADMIN
import ru.itmo.aiex.care.entity.ConsultationRole.CLIENT
import ru.itmo.aiex.care.entity.ConsultationRole.SPECIALIST
import ru.itmo.aiex.care.entity.SessionStatus.CANCELLED
import ru.itmo.aiex.care.entity.SessionStatus.CONFIRMED
import ru.itmo.aiex.care.entity.SessionStatus.DONE
import ru.itmo.aiex.care.entity.SessionStatus.REQUESTED
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import ru.itmo.aiex.common.error.ValidationException
import java.util.stream.Stream

class ConsultationRulesTest {
    enum class Outcome { ALLOWED, FORBIDDEN, CONFLICT }

    @ParameterizedTest(name = "{0}: {1} -> {2} = {3}")
    @MethodSource("allTransitions")
    fun `каждая комбинация роли и перехода статуса`(role: ConsultationRole, from: SessionStatus, to: SessionStatus, expected: Outcome) {
        val change = ConsultationChange(status = to)
        when (expected) {
            Outcome.ALLOWED -> {
                assertThat(ConsultationRules.check(role, from, change)).isEqualTo(to)
            }

            Outcome.FORBIDDEN -> {
                assertThatThrownBy { ConsultationRules.check(role, from, change) }.isInstanceOf(ForbiddenException::class.java)
            }

            Outcome.CONFLICT -> {
                assertThatThrownBy { ConsultationRules.check(role, from, change) }
                    .isInstanceOf(IllegalStateTransitionException::class.java)
                    .extracting { (it as AiExException).code }
                    .isEqualTo(ErrorCode.CONSULTATION_INVALID_STATE)
            }
        }
        assertThat(ConsultationRules.canTransition(role, from, to)).isEqualTo(expected == Outcome.ALLOWED)
    }

    @Test
    fun `целевые статусы ролей`() {
        assertThat(ConsultationRules.targetsFor(CLIENT)).containsExactly(CANCELLED)
        assertThat(ConsultationRules.targetsFor(SPECIALIST)).containsExactlyInAnyOrder(CONFIRMED, DONE, CANCELLED)
        assertThat(ConsultationRules.targetsFor(ADMIN)).containsExactlyInAnyOrder(DONE, CANCELLED)
    }

    @Nested
    inner class `резюме и рекомендации` {
        @ParameterizedTest
        @EnumSource(ConsultationRole::class, names = ["CLIENT", "ADMIN"])
        fun `пишет только специалист`(role: ConsultationRole) {
            assertThatThrownBy {
                ConsultationRules.check(role, CONFIRMED, ConsultationChange(summary = "x"))
            }.isInstanceOf(ForbiddenException::class.java)
            assertThatThrownBy { ConsultationRules.check(role, DONE, ConsultationChange(recommendations = "x")) }
                .isInstanceOf(ForbiddenException::class.java)
        }

        @ParameterizedTest
        @EnumSource(SessionStatus::class, names = ["CONFIRMED", "DONE"])
        fun `специалист ведёт их в CONFIRMED и DONE`(status: SessionStatus) {
            assertThat(ConsultationRules.check(SPECIALIST, status, ConsultationChange(summary = "s", recommendations = "r"))).isEqualTo(status)
        }

        @ParameterizedTest
        @EnumSource(SessionStatus::class, names = ["REQUESTED", "CANCELLED"])
        fun `в остальных статусах - 409`(status: SessionStatus) {
            assertThatThrownBy { ConsultationRules.check(SPECIALIST, status, ConsultationChange(summary = "s")) }
                .isInstanceOf(ConflictException::class.java)
                .extracting { (it as AiExException).code }
                .isEqualTo(ErrorCode.CONSULTATION_INVALID_STATE)
        }

        @Test
        fun `проверяются по итоговому статусу запроса`() {
            assertThat(ConsultationRules.check(SPECIALIST, REQUESTED, ConsultationChange(status = CONFIRMED, summary = "s"))).isEqualTo(CONFIRMED)
            assertThat(ConsultationRules.check(SPECIALIST, CONFIRMED, ConsultationChange(status = DONE, summary = "s"))).isEqualTo(DONE)
            assertThatThrownBy { ConsultationRules.check(SPECIALIST, CONFIRMED, ConsultationChange(status = CANCELLED, summary = "s")) }
                .isInstanceOf(ConflictException::class.java)
        }
    }

    @Nested
    inner class `оценка` {
        @ParameterizedTest
        @EnumSource(ConsultationRole::class, names = ["SPECIALIST", "ADMIN"])
        fun `ставит только клиент`(role: ConsultationRole) {
            assertThatThrownBy { ConsultationRules.check(role, DONE, ConsultationChange(rating = 5)) }.isInstanceOf(ForbiddenException::class.java)
        }

        @ParameterizedTest
        @EnumSource(SessionStatus::class, names = ["REQUESTED", "CONFIRMED", "CANCELLED"])
        fun `только после DONE`(status: SessionStatus) {
            assertThatThrownBy { ConsultationRules.check(CLIENT, status, ConsultationChange(rating = 4)) }.isInstanceOf(ConflictException::class.java)
        }

        @Test
        fun `клиент оценивает проведённую консультацию`() {
            assertThat(ConsultationRules.check(CLIENT, DONE, ConsultationChange(rating = 4))).isEqualTo(DONE)
        }
    }

    @Test
    fun `причина отмены - только вместе с отменой`() {
        assertThatThrownBy { ConsultationRules.check(CLIENT, REQUESTED, ConsultationChange(cancelReason = "причина")) }
            .isInstanceOf(ValidationException::class.java)
            .extracting { (it as ValidationException).violations.single().field }
            .isEqualTo("cancelReason")
        assertThat(ConsultationRules.check(CLIENT, REQUESTED, ConsultationChange(status = CANCELLED, cancelReason = "причина"))).isEqualTo(CANCELLED)
    }

    @Test
    fun `пустое изменение допустимо и ничего не меняет`() {
        SessionStatus.entries.forEach { status ->
            ConsultationRole.entries.forEach { role -> assertThat(ConsultationRules.check(role, status, ConsultationChange())).isEqualTo(status) }
        }
    }

    companion object {
        private val allowed =
            setOf(
                Triple(CLIENT, REQUESTED, CANCELLED),
                Triple(CLIENT, CONFIRMED, CANCELLED),
                Triple(SPECIALIST, REQUESTED, CONFIRMED),
                Triple(SPECIALIST, CONFIRMED, DONE),
                Triple(SPECIALIST, REQUESTED, CANCELLED),
                Triple(SPECIALIST, CONFIRMED, CANCELLED),
                Triple(ADMIN, REQUESTED, DONE),
                Triple(ADMIN, CONFIRMED, DONE),
                Triple(ADMIN, REQUESTED, CANCELLED),
                Triple(ADMIN, CONFIRMED, CANCELLED),
            )

        private val forbiddenTargets =
            mapOf(
                CLIENT to setOf(REQUESTED, CONFIRMED, DONE),
                SPECIALIST to setOf(REQUESTED),
                ADMIN to setOf(REQUESTED, CONFIRMED),
            )

        @JvmStatic
        fun allTransitions(): Stream<Arguments> = ConsultationRole.entries
            .flatMap { role ->
                SessionStatus.entries.flatMap { from ->
                    SessionStatus.entries.map { to ->
                        val outcome =
                            when {
                                Triple(role, from, to) in allowed -> Outcome.ALLOWED
                                to in forbiddenTargets.getValue(role) -> Outcome.FORBIDDEN
                                else -> Outcome.CONFLICT
                            }
                        Arguments.of(role, from, to, outcome)
                    }
                }
            }.stream()
    }
}
