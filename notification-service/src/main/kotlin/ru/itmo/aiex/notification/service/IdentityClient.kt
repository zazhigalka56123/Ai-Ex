package ru.itmo.aiex.notification.service

import feign.codec.Decoder
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import ru.itmo.aiex.iam.dto.UserView
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@FeignClient(name = "iam-service", dismiss404 = true, configuration = [IdentityFeignConfiguration::class])
interface IdentityClient {
    @GetMapping("/internal/users/{id}")
    fun findActive(@PathVariable id: UUID): UserView?
}

class IdentityFeignConfiguration {
    @Bean
    fun identityDecoder(json: JsonMapper): Decoder = Decoder { response, type ->
        if (response.status() == HttpStatus.NOT_FOUND.value() || response.status() == HttpStatus.NO_CONTENT.value()) {
            null
        } else {
            response.body().asInputStream().use { json.readValue(it, json.typeFactory.constructType(type)) }
        }
    }
}
