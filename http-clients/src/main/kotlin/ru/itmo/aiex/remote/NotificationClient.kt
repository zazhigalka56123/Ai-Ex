package ru.itmo.aiex.remote

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@FeignClient(name = "notification-service", configuration = [InternalFeignConfiguration::class])
interface NotificationClient {
    @PostMapping("/internal/notifications")
    fun send(@RequestBody delivery: NotificationDelivery): UUID

    @GetMapping("/internal/metrics")
    fun metrics(): Map<String, Long>
}
