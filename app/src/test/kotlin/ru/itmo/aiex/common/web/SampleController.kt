package ru.itmo.aiex.common.web

import org.springframework.core.MethodParameter
import org.springframework.web.method.HandlerMethod
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.common.web.openapi.ApiErrors
@Suppress("UNUSED_PARAMETER", "unused")
class SampleController {
    fun paged(@PageParams(sortable = ["createdAt", "name"], defaultSort = "createdAt,desc") page: PageQuery) = Unit

    fun pagedWithoutSort(page: PageQuery) = Unit

    fun cursor(@CursorParams(defaultLimit = 10) cursor: CursorQuery) = Unit

    fun cursorDefault(cursor: CursorQuery) = Unit

    fun actor(actor: Actor) = Unit

    fun optionalActor(actor: Actor?) = Unit

    @ApiErrors(ErrorCode.PERSONA_NOT_FOUND, ErrorCode.PERSONA_NOT_READY, ErrorCode.CONCURRENT_MODIFICATION)
    fun documented(actor: Actor, @PageParams(sortable = ["name"], defaultSort = "name,asc") page: PageQuery, cursor: CursorQuery) = Unit

    fun anonymous(value: String) = Unit

    companion object {
        fun method(name: String) = SampleController::class.java.methods.first { it.name == name }

        fun parameter(name: String, index: Int = 0) = MethodParameter(method(name), index)

        fun handler(name: String) = HandlerMethod(SampleController(), method(name))
    }
}
