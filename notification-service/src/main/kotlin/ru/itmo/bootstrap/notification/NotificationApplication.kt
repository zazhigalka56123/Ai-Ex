package ru.itmo.bootstrap.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Bean
import java.time.Clock

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex.notification"])
@EnableFeignClients(basePackages = ["ru.itmo.aiex.notification.client"])
class NotificationApplication {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<NotificationApplication>(*args)
}
