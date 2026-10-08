package ru.itmo.aiex.notification.client

import feign.codec.Decoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
class NotificationFeignConfiguration {
    @Bean
    fun decoder(mapper: JsonMapper): Decoder = Decoder { response, type ->
        if (response.status() == HttpStatus.NO_CONTENT.value() || response.body() == null) {
            null
        } else {
            response.body().asInputStream().use { mapper.readValue(it, mapper.constructType(type)) }
        }
    }
}
