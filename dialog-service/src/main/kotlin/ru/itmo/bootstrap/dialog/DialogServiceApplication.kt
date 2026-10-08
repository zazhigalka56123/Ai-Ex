package ru.itmo.bootstrap.dialog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex"])
@ConfigurationPropertiesScan("ru.itmo.aiex")
@EntityScan("ru.itmo.aiex.dialog.entity", "ru.itmo.aiex.agent.entity", "ru.itmo.aiex.llm.entity")
@EnableJpaRepositories("ru.itmo.aiex.dialog.repository", "ru.itmo.aiex.agent.repository", "ru.itmo.aiex.llm.repository")
@EnableFeignClients(basePackages = ["ru.itmo.aiex.remote"])
@EnableAsync
class DialogServiceApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<DialogServiceApplication>(*args)
}
