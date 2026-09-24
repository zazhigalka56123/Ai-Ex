package ru.itmo.aiex.care.domain

import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import ru.itmo.aiex.common.error.ValidationException

object ConsultationRules {
    private val transitions: Map<ConsultationRole, Map<SessionStatus, Set<SessionStatus>>> =
        mapOf(
            ConsultationRole.CLIENT to
                mapOf(SessionStatus.CANCELLED to SessionStatus.ACTIVE),
            ConsultationRole.SPECIALIST to
                mapOf(
                    SessionStatus.CONFIRMED to setOf(SessionStatus.REQUESTED),
                    SessionStatus.DONE to setOf(SessionStatus.CONFIRMED),
                    SessionStatus.CANCELLED to SessionStatus.ACTIVE,
                ),
            ConsultationRole.ADMIN to
                mapOf(
                    SessionStatus.DONE to SessionStatus.ACTIVE,
                    SessionStatus.CANCELLED to SessionStatus.ACTIVE,
                ),
        )

    private val notesStatuses = setOf(SessionStatus.CONFIRMED, SessionStatus.DONE)

    fun targetsFor(role: ConsultationRole): Set<SessionStatus> = transitions.getValue(role).keys

    fun canTransition(role: ConsultationRole, from: SessionStatus, to: SessionStatus): Boolean = from in transitions.getValue(role)[to].orEmpty()

    fun check(role: ConsultationRole, current: SessionStatus, change: ConsultationChange): SessionStatus {
        val result = change.status?.let { checkTransition(role, current, it) } ?: current
        checkCancelReason(change)
        checkNotes(role, result, change)
        checkRating(role, result, change)
        return result
    }

    private fun checkTransition(role: ConsultationRole, current: SessionStatus, target: SessionStatus): SessionStatus {
        if (target !in targetsFor(role)) {
            throw ForbiddenException("Роль $role не может переводить консультацию в статус $target")
        }
        if (!canTransition(role, current, target)) {
            throw IllegalStateTransitionException(ErrorCode.CONSULTATION_INVALID_STATE, current.name, target.name)
        }
        return target
    }

    private fun checkCancelReason(change: ConsultationChange) {
        if (change.cancelReason != null && change.status != SessionStatus.CANCELLED) {
            throw ValidationException("cancelReason", "cancel.required", "Причину отмены можно указать только вместе со статусом CANCELLED")
        }
    }

    private fun checkNotes(role: ConsultationRole, result: SessionStatus, change: ConsultationChange) {
        if (change.summary == null && change.recommendations == null) return
        if (role != ConsultationRole.SPECIALIST) {
            throw ForbiddenException("Резюме и рекомендации оставляет только специалист консультации")
        }
        if (result !in notesStatuses) {
            throw ConflictException(ErrorCode.CONSULTATION_INVALID_STATE, "Резюме и рекомендации ведутся только в статусах CONFIRMED и DONE")
        }
    }

    private fun checkRating(role: ConsultationRole, result: SessionStatus, change: ConsultationChange) {
        if (change.rating == null) return
        if (role != ConsultationRole.CLIENT) {
            throw ForbiddenException("Оценку ставит только клиент консультации")
        }
        if (result != SessionStatus.DONE) {
            throw ConflictException(ErrorCode.CONSULTATION_INVALID_STATE, "Оценить можно только проведённую консультацию")
        }
    }
}
