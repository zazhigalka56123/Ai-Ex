package ru.itmo.aiex.common.web

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import ru.itmo.aiex.common.error.UnauthenticatedException
import ru.itmo.aiex.common.security.Actor
import java.util.UUID

class CurrentActorArgumentResolver(private val lookup: ActorLookup) : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == Actor::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Actor? {
        val raw = webRequest.getHeader(AiExHeaders.USER_ID)?.trim()
        if (raw.isNullOrEmpty()) {
            if (parameter.isOptional) return null
            throw UnauthenticatedException("Не передан заголовок ${AiExHeaders.USER_ID}")
        }
        val userId =
            runCatching { UUID.fromString(raw) }.getOrNull()
                ?: throw UnauthenticatedException("Заголовок ${AiExHeaders.USER_ID} должен содержать UUID")
        return lookup.findActor(userId)
            ?: throw UnauthenticatedException("Пользователь $userId не найден или заблокирован")
    }
}
