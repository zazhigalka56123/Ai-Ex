package ru.itmo.aiex.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.itmo.aiex.common.ExcludeFromCoverage
@ExcludeFromCoverage
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {
    @Bean
    fun aiExOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("«Привет, спишь?» - ai-ex API")
            .version("v1")
            .description(
                """
                    Монолит лабораторной №1. Текущий пользователь передаётся заголовком `X-User-Id` (в лаб. 3 - JWT).
                    Демо-пользователи (контекст Liquibase `demo`): администратор `00000000-0000-0000-0000-00000000a001`,
                    специалист `00000000-0000-0000-0000-00000000b001`, клиент `00000000-0000-0000-0000-00000000c001`.
                    Все ошибки - `application/problem+json` (RFC 9457) с полями `code` и `traceId`.
                """.trimIndent(),
            ),
    )

    @Bean
    fun allGroup(): GroupedOpenApi = group("all", "ru.itmo.aiex")

    @Bean
    fun iamGroup(): GroupedOpenApi = group("iam", "ru.itmo.aiex.iam")

    @Bean
    fun personaGroup(): GroupedOpenApi = group("persona", "ru.itmo.aiex.persona")

    @Bean
    fun ingestGroup(): GroupedOpenApi = group("ingest", "ru.itmo.aiex.ingest")

    @Bean
    fun dialogGroup(): GroupedOpenApi = group("dialog", "ru.itmo.aiex.dialog")

    @Bean
    fun careGroup(): GroupedOpenApi = group("care", "ru.itmo.aiex.care")

    @Bean
    fun adminGroup(): GroupedOpenApi = group("admin", "ru.itmo.aiex.admin")

    @Bean
    fun notificationGroup(): GroupedOpenApi = group("notification", "ru.itmo.aiex.notification")

    private fun group(name: String, vararg packages: String): GroupedOpenApi = GroupedOpenApi
        .builder()
        .group(name)
        .packagesToScan(*packages)
        .pathsToMatch("/api/**")
        .build()
}
