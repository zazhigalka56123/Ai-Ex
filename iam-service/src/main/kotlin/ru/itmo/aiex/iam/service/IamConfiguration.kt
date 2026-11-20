package ru.itmo.aiex.iam.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class IamConfiguration {
    private companion object {
        const val BCRYPT_STRENGTH = 12
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
