package ru.itmo.bootstrap.account

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex.iam"])
@EntityScan("ru.itmo.aiex.iam.entity")
@EnableJpaRepositories("ru.itmo.aiex.iam.repository")
class AccountServiceApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<AccountServiceApplication>(*args)
}
