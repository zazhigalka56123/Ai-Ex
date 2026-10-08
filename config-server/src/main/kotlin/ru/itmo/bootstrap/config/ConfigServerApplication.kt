package ru.itmo.bootstrap.config

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.config.server.EnableConfigServer

@EnableConfigServer
@SpringBootApplication
class ConfigServerApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<ConfigServerApplication>(*args)
}
