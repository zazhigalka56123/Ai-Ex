package ru.itmo.aiex.web

import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import ru.itmo.aiex.common.ExcludeFromCoverage

@ExcludeFromCoverage
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaginationProperties::class)
class WebCommonConfiguration(private val paginationProperties: PaginationProperties, private val actorLookup: ObjectProvider<ActorLookup>) :
    WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers += CurrentActorArgumentResolver { userId -> actorLookup.getObject().findActor(userId) }
        resolvers += PageQueryArgumentResolver(paginationProperties)
        resolvers += CursorQueryArgumentResolver(paginationProperties)
    }
}
