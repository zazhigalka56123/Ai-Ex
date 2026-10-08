package ru.itmo.bootstrap.care

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex"])
@ConfigurationPropertiesScan("ru.itmo.aiex")
@EntityScan("ru.itmo.aiex.care.entity", "ru.itmo.aiex.admin.entity")
@EnableJpaRepositories("ru.itmo.aiex.care.repository", "ru.itmo.aiex.admin.repository")
@EnableFeignClients(basePackages = ["ru.itmo.aiex.remote"])
@EnableAsync
class CareServiceApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<CareServiceApplication>(*args)
}
