package ru.itmo.bootstrap.persona

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex"])
@ConfigurationPropertiesScan("ru.itmo.aiex")
@EntityScan("ru.itmo.aiex.persona.entity", "ru.itmo.aiex.ingest.entity")
@EnableJpaRepositories("ru.itmo.aiex.persona.repository", "ru.itmo.aiex.ingest.repository")
@EnableFeignClients(basePackages = ["ru.itmo.aiex.remote"])
@EnableAsync
class PersonaServiceApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<PersonaServiceApplication>(*args)
}
