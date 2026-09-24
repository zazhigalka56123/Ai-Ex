package ru.itmo.aiex.care.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.care.domain.ConsultationChange
import ru.itmo.aiex.care.domain.ConsultationSession
import ru.itmo.aiex.care.domain.SessionStatus
import ru.itmo.aiex.care.domain.SpecialistSlot
import ru.itmo.aiex.care.domain.port.ConsultationRepository
import ru.itmo.aiex.care.domain.port.SlotRepository
import ru.itmo.aiex.care.domain.port.SpecialistRepository
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.SlotAlreadyBookedException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.events.ConsultationRequested
import ru.itmo.aiex.common.events.ConsultationStatusChanged
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.common.time.nowMicros
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ConsultationService(
    private val sessions: ConsultationRepository,
    private val slots: SlotRepository,
    private val specialists: SpecialistRepository,
    private val events: DomainEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun book(actor: Actor, command: BookConsultationCommand): ConsultationSession {
        actor.requireRole(RoleCode.USER)
        val slot = slots.findByIdForUpdate(command.slotId) ?: throw NotFoundException.of(ErrorCode.SLOT_NOT_FOUND, command.slotId)
        val now = clock.nowMicros()
        checkBookable(actor, slot, now)
        val session =
            ConsultationSession(
                id = Ids.next(),
                userId = actor.userId,
                specialist = slot.specialist,
                slot = slot,
                sharedConversationId = command.sharedConversationId,
                createdAt = now,
            )
        val saved =
            try {
                sessions.saveAndFlush(session)
            } catch (ex: DataIntegrityViolationException) {
                throw SlotAlreadyBookedException().apply { addSuppressed(ex) }
            }
        specialists.incrementBookedCount(slot.specialist.id)
        events.publish(ConsultationRequested(saved.id, actor.userId, slot.specialist.userId, saved.startsAt, now))
        return saved
    }

    fun list(actor: Actor, status: SessionStatus?, page: PageQuery): PageView<ConsultationSession> =
        sessions.findPageForParticipant(actor.userId, status, page)

    fun get(actor: Actor, id: UUID): ConsultationSession = sessions.findById(id)?.takeIf { it.roleOf(actor) != null } ?: throw notFound(id)

    @Transactional
    fun update(actor: Actor, id: UUID, change: ConsultationChange): ConsultationSession {
        val session = sessions.findById(id) ?: throw notFound(id)
        val role = session.roleOf(actor) ?: throw notFound(id)
        val now = clock.nowMicros()
        val statusChanged = session.applyChange(role, change, now)
        val saved = sessions.saveAndFlush(session)
        if (statusChanged) {
            if (saved.status == SessionStatus.CANCELLED) specialists.decrementBookedCount(saved.specialist.id)
            events.publish(ConsultationStatusChanged(saved.id, saved.userId, saved.specialist.userId, saved.status.name, now))
        }
        return saved
    }

    private fun checkBookable(actor: Actor, slot: SpecialistSlot, now: Instant) {
        val specialist = slot.specialist
        if (!specialist.isActive) throw NotFoundException.of(ErrorCode.SLOT_NOT_FOUND, slot.id)
        if (specialist.isOwnedBy(actor.userId)) throw ForbiddenException("Специалист не может записаться на консультацию к самому себе")
        if (!slot.startsAt.isAfter(now)) throw ValidationException("slotId", "slot.past", "Слот уже начался или прошёл")
        if (sessions.existsActiveOnSlot(slot.id)) throw SlotAlreadyBookedException()
    }

    private fun notFound(id: UUID) = NotFoundException.of(ErrorCode.CONSULTATION_NOT_FOUND, id)
}
