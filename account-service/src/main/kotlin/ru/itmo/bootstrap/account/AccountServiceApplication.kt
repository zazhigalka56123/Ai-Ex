package ru.itmo.bootstrap.account

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex"])
@ConfigurationPropertiesScan("ru.itmo.aiex")
@EntityScan("ru.itmo.aiex.iam.entity")
@EnableJpaRepositories("ru.itmo.aiex.iam.repository")
@EnableFeignClients(basePackages = ["ru.itmo.aiex.remote"])
@EnableAsync
class AccountServiceApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<AccountServiceApplication>(*args)
}
