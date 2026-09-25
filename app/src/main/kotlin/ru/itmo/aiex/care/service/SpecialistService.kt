package ru.itmo.aiex.care.service

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.care.entity.Specialist
import ru.itmo.aiex.care.entity.Specialization
import ru.itmo.aiex.care.repository.SpecialistRepository
import ru.itmo.aiex.care.repository.SpecializationRepository
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
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SpecialistService(
    private val specialists: SpecialistRepository,
    private val specializations: SpecializationRepository,
    private val clock: Clock,
) {
    @Transactional
    fun create(actor: Actor, command: CreateSpecialistCommand): Specialist {
        actor.requireRole(RoleCode.SPECIALIST)
        if (specialists.existsByUserId(actor.userId)) throw profileExists(actor.userId)
        val now = clock.nowMicros()
        val specialist =
            Specialist(
                id = Ids.next(),
                userId = actor.userId,
                headline = command.headline.trim(),
                bio = command.bio.trim(),
                pricePerHour = command.pricePerHour,
                createdAt = now,
            )
        specialist.replaceSpecializations(resolveSpecializations(command.specializationCodes), now)
        return try {
            specialists.saveAndFlush(specialist)
        } catch (ex: DataIntegrityViolationException) {
            throw profileExists(actor.userId).apply { addSuppressed(ex) }
        }
    }

    fun catalog(specializationCode: String?, page: PageQuery): PageView<Specialist> =
        specialists.findActivePage(specializationCode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }, page)

    fun get(actor: Actor?, id: UUID): Specialist = findVisible(actor, id)

    @Transactional
    fun update(actor: Actor, id: UUID, command: UpdateSpecialistCommand): Specialist {
        val specialist = findVisible(actor, id)
        if (!specialist.canBeManagedBy(actor)) throw ForbiddenException("Профиль специалиста правит только его владелец или администратор")
        val now = clock.nowMicros()
        specialist.describe(
            headline = command.headline?.let { requireText("headline", it) },
            bio = command.bio?.let { requireText("bio", it) },
            pricePerHour = command.pricePerHour,
            now = now,
        )
        command.status?.let { specialist.changeStatus(it, now) }
        command.specializationCodes?.let { specialist.replaceSpecializations(resolveSpecializations(it), now) }
        return specialists.saveAndFlush(specialist)
    }

    private fun findVisible(actor: Actor?, id: UUID): Specialist =
        specialists.findById(id)?.takeIf { it.isVisibleTo(actor) } ?: throw NotFoundException.of(ErrorCode.SPECIALIST_NOT_FOUND, id)

    private fun resolveSpecializations(codes: Set<String>): List<Specialization> {
        val normalized = codes.map { it.trim().lowercase() }.toSet()
        if (normalized.isEmpty()) throw ValidationException(SPECIALIZATION_CODES, "size", "Нужна хотя бы одна специализация")
        val found = specializations.findByCodes(normalized)
        val unknown = normalized - found.map { it.code }.toSet()
        if (unknown.isNotEmpty()) {
            throw ValidationException(SPECIALIZATION_CODES, "unknown", "Неизвестные специализации: ${unknown.sorted().joinToString()}")
        }
        return found
    }

    private fun requireText(field: String, value: String): String =
        value.trim().ifEmpty { throw ValidationException(field, "required", "Поле не может быть пустым") }

    private fun profileExists(userId: UUID) =
        ConflictException(ErrorCode.SPECIALIST_PROFILE_EXISTS, "У пользователя $userId уже есть профиль специалиста")

    private companion object {
        const val SPECIALIZATION_CODES = "specializationCodes"
    }
}
