package ru.itmo.aiex.care.controller

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.itmo.aiex.admin.service.GuardrailFlagListener
import ru.itmo.aiex.care.service.ConsultationQuery
import ru.itmo.aiex.common.events.MessageAutoFlagged
import java.util.UUID

@RestController
@Profile("microservice")
class CareInternalController(private val consultations: ConsultationQuery, private val guardrails: GuardrailFlagListener) {
    @GetMapping("/internal/consultations/shared")
    fun isConversationShared(@RequestParam conversationId: UUID, @RequestParam specialistUserId: UUID) =
        consultations.isConversationSharedWith(conversationId, specialistUserId)

    @GetMapping("/internal/consultations/active")
    fun hasActiveConsultation(@RequestParam userId: UUID, @RequestParam specialistId: UUID) = consultations.hasActiveSession(userId, specialistId)

    @PostMapping("/internal/events/message-auto-flagged")
    fun registerGuardrailFlag(@RequestBody event: MessageAutoFlagged) = guardrails.onMessageAutoFlagged(event)
}
