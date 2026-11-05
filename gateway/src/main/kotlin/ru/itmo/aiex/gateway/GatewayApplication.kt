package ru.itmo.aiex.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import ru.itmo.aiex.security.ReactiveSecurityConfiguration

@Import(ReactiveSecurityConfiguration::class)
@SpringBootApplication
class GatewayApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
