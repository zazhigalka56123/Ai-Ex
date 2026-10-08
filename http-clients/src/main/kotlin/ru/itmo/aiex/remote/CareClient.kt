package ru.itmo.aiex.remote

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import ru.itmo.aiex.common.events.MessageAutoFlagged
import java.util.UUID

@FeignClient(name = "care-service", configuration = [InternalFeignConfiguration::class])
interface CareClient {
    @GetMapping("/internal/consultations/shared")
    fun isConversationSharedWith(
        @RequestParam("conversationId") conversationId: UUID,
        @RequestParam("specialistUserId") specialistUserId: UUID,
    ): Boolean

    @GetMapping("/internal/consultations/active")
    fun hasActiveSession(@RequestParam("userId") userId: UUID, @RequestParam("specialistId") specialistId: UUID): Boolean

    @PostMapping("/internal/events/message-auto-flagged")
    fun messageAutoFlagged(@RequestBody event: MessageAutoFlagged)

    @GetMapping("/internal/metrics")
    fun getMetrics(): Map<String, Long>
}
