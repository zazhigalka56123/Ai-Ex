package ru.itmo.aiex.admin.application

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ru.itmo.aiex.admin.domain.FlagStatus
import ru.itmo.aiex.admin.domain.ModerationFlag
import ru.itmo.aiex.common.error.AiExException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.NotFoundException
import ru.itmo.aiex.common.moderation.FlagReason
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.security.RoleCode
import ru.itmo.aiex.dialog.api.DialogQuery
import ru.itmo.aiex.persona.api.PersonaLifecycle
import java.util.UUID

@Component
class ModerationDesk(
    private val moderation: ModerationService,
    @param:Lazy private val dialogs: DialogQuery,
    @param:Lazy private val personas: PersonaLifecycle,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun report(actor: Actor, command: ReportMessageCommand): ModerationFlag {
        actor.requireAnyRole(RoleCode.USER, RoleCode.SPECIALIST)
        val message =
            dialogs.findMessageVisibleTo(command.messageId, actor)
                ?: throw NotFoundException.of(ErrorCode.MESSAGE_NOT_FOUND, command.messageId)
        return moderation.report(actor, message.toFlagged(), command)
    }

    fun list(actor: Actor, status: FlagStatus?, reason: FlagReason?, page: PageQuery): PageView<FlagView> =
        moderation.list(actor, status, reason, page).map { withPreview(it) }

    fun get(actor: Actor, id: UUID): FlagView = withPreview(moderation.get(actor, id))

    fun review(actor: Actor, id: UUID, command: ReviewFlagCommand): FlagView {
        val flag = moderation.review(actor, id, command)
        val archived = if (command.archivePersona) archivePersona(flag, actor) else null
        return withPreview(flag, archived)
    }

    private fun archivePersona(flag: ModerationFlag, actor: Actor): Boolean = try {
        personas.archive(flag.personaId, actor)
        true
    } catch (ex: AiExException) {
        log.warn("Флаг {} разобран, но персона {} не архивирована: {}", flag.id, flag.personaId, ex.code, ex)
        false
    }

    private fun withPreview(flag: ModerationFlag, personaArchived: Boolean? = null) =
        FlagView(flag, dialogs.findMessage(flag.messageId), personaArchived)
}
