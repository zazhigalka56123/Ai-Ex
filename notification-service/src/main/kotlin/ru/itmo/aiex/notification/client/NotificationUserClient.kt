package ru.itmo.aiex.notification.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import ru.itmo.aiex.iam.dto.UserView
import ru.itmo.aiex.remote.InternalFeignConfiguration
import java.util.UUID

@FeignClient(name = "account-service", contextId = "notificationUserLookup", configuration = [InternalFeignConfiguration::class])
interface NotificationUserClient {
    @GetMapping("/internal/users/{userId}")
    fun findActive(@PathVariable userId: UUID): UserView?
}
