package ru.itmo.aiex.admin.application

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.itmo.aiex.admin.domain.FlagStatus
import ru.itmo.aiex.admin.domain.FlaggedMessage
import ru.itmo.aiex.admin.domain.ModerationFlag
import ru.itmo.aiex.admin.domain.port.ModerationFlagRepository
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.error.ValidationException
import ru.itmo.aiex.common.events.DomainEventPublisher
import ru.itmo.aiex.common.events.ModerationFlagRaised
import ru.itmo.aiex.common.events.ModerationFlagResolved
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.common.time.nowMicros
import java.time.Clock
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ModerationService(private val flags: ModerationFlagRepository, private val events: DomainEventPublisher, private val clock: Clock) {
    @Transactional
    fun report(actor: Actor, message: FlaggedMessage, command: ReportMessageCommand): ModerationFlag {
        actor.requireAnyRole(RoleCode.USER, RoleCode.SPECIALIST)
        if (flags.existsByMessageAndReporter(message.messageId, actor.userId)) throw alreadyReported(message.messageId)
        val now = clock.nowMicros()
        val flag = ModerationFlag.reportedBy(Ids.next(), message, actor.userId, command.reason, command.comment?.trim()?.ifEmpty { null }, now)
        val saved =
            try {
                flags.saveAndFlush(flag)
            } catch (ex: DataIntegrityViolationException) {
                throw alreadyReported(message.messageId).apply { addSuppressed(ex) }
            }
        events.publish(ModerationFlagRaised(saved.id, saved.messageId, saved.reason, now))
        return saved
    }

    fun list(actor: Actor, status: FlagStatus?, reason: FlagReason?, page: PageQuery): PageView<ModerationFlag> {
        actor.requireRole(RoleCode.ADMIN)
        return flags.findPage(status, reason, page)
    }

    fun get(actor: Actor, id: UUID): ModerationFlag {
        actor.requireRole(RoleCode.ADMIN)
        return flags.findById(id) ?: throw NotFoundException.of(ErrorCode.FLAG_NOT_FOUND, id)
    }

    @Transactional
    fun review(actor: Actor, id: UUID, command: ReviewFlagCommand): ModerationFlag {
        val flag = get(actor, id)
        if (command.archivePersona && command.status != FlagStatus.RESOLVED) {
            throw ValidationException("archivePersona", "status.resolved", "Архивировать персону можно только вместе с вердиктом RESOLVED")
        }
        val now = clock.nowMicros()
        flag.review(command.status, command.resolution?.trim()?.ifEmpty { null }, actor.userId, now)
        val saved = flags.saveAndFlush(flag)
        if (saved.status.isTerminal) {
            events.publish(ModerationFlagResolved(saved.id, saved.messageId, saved.status.name, saved.reporterId, now))
        }
        return saved
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun raiseGuardrailFlag(message: FlaggedMessage, reason: FlagReason, details: String): ModerationFlag? {
        if (flags.existsGuardrailFlag(message.messageId, reason)) return null
        val now = clock.nowMicros()
        val saved = flags.saveAndFlush(ModerationFlag.raisedByGuardrail(Ids.next(), message, reason, details, now))
        events.publish(ModerationFlagRaised(saved.id, saved.messageId, saved.reason, now))
        return saved
    }

    private fun alreadyReported(messageId: UUID) = ConflictException(ErrorCode.FLAG_ALREADY_REPORTED, "Жалоба на сообщение $messageId уже подана")
}
