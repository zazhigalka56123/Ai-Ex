package ru.itmo.aiex.care.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

import ru.itmo.aiex.care.entity.Specialist
import ru.itmo.aiex.care.entity.SpecialistSlot
import ru.itmo.aiex.care.repository.SlotRepository
import ru.itmo.aiex.care.repository.SpecialistRepository
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.ForbiddenException
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.common.time.nowMicros
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SlotService(private val specialists: SpecialistRepository, private val slots: SlotRepository, private val clock: Clock) {
    @Transactional
    fun create(actor: Actor, specialistId: UUID, command: CreateSlotCommand): SpecialistSlot {
        actor.requireRole(RoleCode.SPECIALIST)
        val specialist = visible(specialists.findByIdForUpdate(specialistId), actor, specialistId)
        if (!specialist.isOwnedBy(actor.userId)) throw ForbiddenException("Слоты публикует только владелец профиля")
        val now = clock.nowMicros()
        val startsAt = command.startsAt.truncatedTo(ChronoUnit.MICROS)
        if (!startsAt.isAfter(now)) throw ValidationException("startsAt", "future", "Слот должен начинаться в будущем")
        val overlapping =
            slots
                .findStartingBetween(specialistId, SlotSchedule.candidatesFrom(startsAt), SlotSchedule.end(startsAt, command.durationMin))
                .any { it.overlaps(startsAt, command.durationMin) }
        if (overlapping) throw slotOverlap()
        return try {
            slots.add(SpecialistSlot(Ids.next(), specialist, startsAt, command.durationMin, now))
        } catch (ex: DataIntegrityViolationException) {
            throw slotOverlap().apply { addSuppressed(ex) }
        }
    }

    fun get(actor: Actor?, specialistId: UUID, slotId: UUID): SpecialistSlot {
        visible(specialists.findById(specialistId), actor, specialistId)
        return slots.findById(slotId)?.takeIf { it.specialist.id == specialistId } ?: throw NotFoundException.of(ErrorCode.SLOT_NOT_FOUND, slotId)
    }

    fun listFree(actor: Actor?, specialistId: UUID, from: Instant?, to: Instant?, page: PageQuery): PageView<SpecialistSlot> {
        if (from != null && to != null && !to.isAfter(from)) throw ValidationException("to", "range", "Граница to должна быть позже from")
        visible(specialists.findById(specialistId), actor, specialistId)
        val now = clock.nowMicros()

        val lower = from?.minusNanos(NANOS_PER_MICRO)?.takeIf { it.isAfter(now) } ?: now
        return slots.findFreePage(specialistId, lower, to ?: FAR_FUTURE, page)
    }

    private fun visible(specialist: Specialist?, actor: Actor?, id: UUID): Specialist =
        specialist?.takeIf { it.isVisibleTo(actor) } ?: throw NotFoundException.of(ErrorCode.SPECIALIST_NOT_FOUND, id)

    private fun slotOverlap() = ConflictException(ErrorCode.SLOT_OVERLAP, "Слот пересекается с уже опубликованным слотом этого специалиста")

    private companion object {
        const val NANOS_PER_MICRO = 1_000L
        val FAR_FUTURE: Instant = Instant.parse("9999-12-31T23:59:59Z")
    }
}
