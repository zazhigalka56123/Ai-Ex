package ru.itmo.aiex

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
class AiExApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<AiExApplication>(*args)
}
