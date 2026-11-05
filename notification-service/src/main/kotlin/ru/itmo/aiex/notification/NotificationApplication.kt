package ru.itmo.aiex.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Import
import ru.itmo.aiex.security.ReactiveSecurityConfiguration

@Import(ReactiveSecurityConfiguration::class)
@SpringBootApplication
@EnableFeignClients
class NotificationApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<NotificationApplication>(*args)
}
