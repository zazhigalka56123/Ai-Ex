package ru.itmo.aiex.remote

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import ru.itmo.aiex.agent.dto.DescribePersonaCommand
import ru.itmo.aiex.agent.dto.PersonaDescription
import ru.itmo.aiex.common.events.ModerationFlagRaised
import ru.itmo.aiex.common.events.PersonaArchived
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.dialog.dto.MessageView
import java.util.UUID

@FeignClient(name = "dialog-service", configuration = [InternalFeignConfiguration::class])
interface DialogClient {
    @GetMapping("/internal/messages/{id}")
    fun findMessage(@PathVariable("id") id: UUID): MessageView?

    @PostMapping("/internal/messages/{id}/visibility")
    fun findMessageVisibleTo(@PathVariable("id") id: UUID, @RequestBody actor: Actor): MessageView?

    @GetMapping("/internal/conversations/{id}/owned")
    fun isConversationOwnedBy(@PathVariable("id") id: UUID, @RequestParam("userId") userId: UUID): Boolean

    @PostMapping("/internal/persona-descriptions")
    fun describe(@RequestBody command: DescribePersonaCommand): PersonaDescription

    @PostMapping("/internal/events/persona-archived")
    fun personaArchived(@RequestBody event: PersonaArchived)

    @PostMapping("/internal/events/moderation-raised")
    fun moderationRaised(@RequestBody event: ModerationFlagRaised)

    @GetMapping("/internal/metrics")
    fun metrics(): Map<String, Long>
}
