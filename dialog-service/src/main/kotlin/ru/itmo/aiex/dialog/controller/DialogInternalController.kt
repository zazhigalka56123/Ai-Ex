package ru.itmo.aiex.dialog.controller

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.agent.dto.DescribePersonaCommand
import ru.itmo.aiex.agent.service.PersonaDescriber
import ru.itmo.aiex.common.events.ModerationFlagRaised
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.dialog.service.DialogEventListeners
import ru.itmo.aiex.dialog.service.DialogQuery
import java.util.UUID

@RestController
@Profile("microservice")
class DialogInternalController(
    private val dialogs: DialogQuery,
    private val descriptions: PersonaDescriber,
    private val listeners: DialogEventListeners,
) {
    @GetMapping("/internal/messages/{id}")
    fun findMessage(@PathVariable id: UUID) = dialogs.findMessage(id)

    @PostMapping("/internal/messages/{id}/visibility")
    fun findVisibleMessage(@PathVariable id: UUID, @RequestBody actor: Actor) = dialogs.findMessageVisibleTo(id, actor)

    @GetMapping("/internal/conversations/{id}/owned")
    fun isConversationOwned(@PathVariable id: UUID, @RequestParam userId: UUID) = dialogs.isConversationOwnedBy(id, userId)

    @PostMapping("/internal/persona-descriptions")
    fun describePersona(@RequestBody command: DescribePersonaCommand) = descriptions.describe(command)

    @PostMapping("/internal/events/persona-archived")
    fun archivePersonaConversations(@RequestBody event: PersonaArchived) = listeners.on(event)

    @PostMapping("/internal/events/moderation-raised")
    fun flagMessage(@RequestBody event: ModerationFlagRaised) = listeners.on(event)
}
