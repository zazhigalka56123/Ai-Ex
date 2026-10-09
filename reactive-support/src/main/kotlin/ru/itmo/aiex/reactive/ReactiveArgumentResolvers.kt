package ru.itmo.aiex.reactive

import org.springframework.core.MethodParameter
import org.springframework.web.reactive.BindingContext
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver
import org.springframework.web.reactive.result.method.SyncHandlerMethodArgumentResolver
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import ru.itmo.aiex.common.error.UnauthenticatedException
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.AiExHeaders
import ru.itmo.aiex.common.web.PageParams
import ru.itmo.aiex.common.web.PageQueryParser
import java.util.UUID

/** Параметр контроллера типа [Actor] - текущий пользователь из `X-User-Id`, найденный без блокировки event loop. */
class ActorArgumentResolver(private val lookup: () -> ReactiveActorLookup) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == Actor::class.java

    override fun resolveArgument(parameter: MethodParameter, bindingContext: BindingContext, exchange: ServerWebExchange): Mono<Any> = Mono.defer {
        val raw = exchange.request.headers.getFirst(AiExHeaders.USER_ID)?.trim()
        if (raw.isNullOrEmpty()) {
            return@defer Mono.error(UnauthenticatedException("Не передан заголовок ${AiExHeaders.USER_ID}"))
        }
        val userId =
            runCatching { UUID.fromString(raw) }.getOrNull()
                ?: return@defer Mono.error(UnauthenticatedException("Заголовок ${AiExHeaders.USER_ID} должен содержать UUID"))
        lookup()
            .findActor(userId)
            .switchIfEmpty(Mono.error { UnauthenticatedException("Пользователь $userId не найден или заблокирован") })
            .cast(Any::class.java)
    }
}

/** Параметр типа [PageQuery] по тем же правилам, что и в Spring MVC: `page`, `size ≤ max-size`, `sort` из белого списка. */
class PageQueryArgumentResolver(private val parser: PageQueryParser) : SyncHandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == PageQuery::class.java

    override fun resolveArgumentValue(parameter: MethodParameter, bindingContext: BindingContext, exchange: ServerWebExchange): Any {
        val params = exchange.request.queryParams
        val settings = parameter.getParameterAnnotation(PageParams::class.java)
        return parser.parse(params.getFirst("page"), params.getFirst("size"), params["sort"], settings)
    }
}
