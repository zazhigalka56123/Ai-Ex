package ru.itmo.aiex.notification.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import ru.itmo.aiex.notification.dto.NotificationCommand
import ru.itmo.aiex.notification.repository.NotificationRepository
import ru.itmo.aiex.notification.service.NotificationService
import java.util.UUID

@RestController
@RequestMapping("/internal")
class InternalNotificationController(private val notifications: NotificationService, private val repository: NotificationRepository) {
    @PostMapping("/notifications")
    fun sendNotification(@RequestBody command: NotificationCommand): Mono<UUID> = notifications.sendNotification(command)

    @GetMapping("/metrics")
    fun getMetrics(): Mono<Map<String, Long>> = repository.countByStatus()
}
