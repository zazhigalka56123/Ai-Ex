package ru.itmo.aiex.iam

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["ru.itmo.aiex.iam", "ru.itmo.aiex.common.web"])
class IamApplication

@Suppress("SpreadOperator")
fun main(args: Array<String>) {
    runApplication<IamApplication>(*args)
}
