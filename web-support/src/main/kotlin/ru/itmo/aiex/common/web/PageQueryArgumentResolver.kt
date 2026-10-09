package ru.itmo.aiex.common.web

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import ru.itmo.aiex.common.paging.PageQuery
class PageQueryArgumentResolver(properties: PaginationProperties) : HandlerMethodArgumentResolver {
    private val parser = PageQueryParser(properties.defaultSize, properties.maxSize)

    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == PageQuery::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): PageQuery = parser.parse(
        webRequest.getParameter("page"),
        webRequest.getParameter("size"),
        webRequest.getParameterValues("sort")?.toList(),
        parameter.getParameterAnnotation(PageParams::class.java),
    )
}
