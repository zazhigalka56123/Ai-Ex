package ru.itmo.aiex.iam.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class IamConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
