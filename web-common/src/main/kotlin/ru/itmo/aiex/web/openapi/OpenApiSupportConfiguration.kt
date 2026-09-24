package ru.itmo.aiex.web.openapi

import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.itmo.aiex.common.ExcludeFromCoverage
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.web.PaginationProperties

@ExcludeFromCoverage
@Configuration(proxyBeanMethods = false)
class OpenApiSupportConfiguration {
    init {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(Actor::class.java, PageQuery::class.java, CursorQuery::class.java)
    }

    @Bean
    fun aiExOperationCustomizer(pagination: PaginationProperties) = AiExOperationCustomizer(pagination)

    @Bean
    fun problemSchemaCustomizer() = ProblemSchemaCustomizer()
}
