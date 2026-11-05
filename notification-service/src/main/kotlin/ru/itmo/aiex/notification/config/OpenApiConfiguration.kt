package ru.itmo.aiex.notification.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    fun serviceOpenApi(): OpenAPI = OpenAPI()
        .info(Info().title("Notifications API").version("v1"))
        .servers(listOf(Server().url("/")))
}
