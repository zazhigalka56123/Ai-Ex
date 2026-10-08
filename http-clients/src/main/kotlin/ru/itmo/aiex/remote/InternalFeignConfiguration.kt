package ru.itmo.aiex.remote

import feign.RequestInterceptor
import feign.codec.ErrorDecoder
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.json.JsonMapper

@Configuration(proxyBeanMethods = false)
class InternalFeignConfiguration {
    @Bean
    fun internalRequestInterceptor(@Value("\${aiex.internal-token:ai-ex-local-token}") token: String): RequestInterceptor =
        RequestInterceptor { request ->
            request.header(InternalApi.TOKEN_HEADER, token)
            MDC.get("traceId")?.let { request.header("X-Trace-Id", it) }
        }

    @Bean
    fun remoteErrorDecoder(mapper: JsonMapper): ErrorDecoder = RemoteErrorDecoder(mapper)

    @Bean
    fun circuitBreakerNameResolver(): CircuitBreakerNameResolver = CircuitBreakerNameResolver { name, _, _ -> name }
}
