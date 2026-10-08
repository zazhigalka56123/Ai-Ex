package ru.itmo.aiex.remote

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import ru.itmo.aiex.iam.dto.UserView
import java.util.UUID

@FeignClient(name = "account-service", configuration = [InternalFeignConfiguration::class])
interface AccountClient {
    @GetMapping("/internal/users/{userId}")
    fun findActive(@PathVariable("userId") userId: UUID): UserView?

    @GetMapping("/internal/metrics")
    fun metrics(): Map<String, Long>
}
