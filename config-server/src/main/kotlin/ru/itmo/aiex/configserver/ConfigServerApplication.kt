package ru.itmo.aiex.configserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.config.server.EnableConfigServer

@SpringBootApplication
@EnableConfigServer
class ConfigServerApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<ConfigServerApplication>(*args)
}
