package ru.itmo.aiex.reactive

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.web.PageQueryParser
import java.time.Clock

/** Общая инфраструктура WebFlux-сервисов. В servlet-приложении (монолит `app`) не включается. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@Import(ReactiveExceptionHandler::class)
class ReactiveWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun pageQueryParser(
        @Value("\${ai-ex.pagination.default-size:${PageQuery.DEFAULT_SIZE}}") defaultSize: Int,
        @Value("\${ai-ex.pagination.max-size:${PageQuery.MAX_SIZE}}") maxSize: Int,
    ): PageQueryParser = PageQueryParser(defaultSize, maxSize)

    @Bean
    fun internalTokenWebFilter(@Value("\${aiex.internal-token:ai-ex-local-token}") token: String): InternalTokenWebFilter =
        InternalTokenWebFilter(token)

    @Bean
    fun aiExArgumentResolvers(parser: PageQueryParser, actors: ObjectProvider<ReactiveActorLookup>): WebFluxConfigurer = object : WebFluxConfigurer {
        override fun configureArgumentResolvers(configurer: ArgumentResolverConfigurer) {
            configurer.addCustomResolver(ActorArgumentResolver { actors.getObject() }, PageQueryArgumentResolver(parser))
        }
    }
}
