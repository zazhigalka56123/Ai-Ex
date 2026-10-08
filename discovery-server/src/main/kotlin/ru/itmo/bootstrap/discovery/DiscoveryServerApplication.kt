package ru.itmo.bootstrap.discovery

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer

@EnableEurekaServer
@SpringBootApplication
class DiscoveryServerApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DiscoveryServerApplication>(*args)
}
