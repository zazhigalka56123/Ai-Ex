package ru.itmo.bootstrap.notification

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex.notification"])
@EnableFeignClients(basePackages = ["ru.itmo.aiex.notification.client"])
class NotificationApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<NotificationApplication>(*args)
}
