package ru.itmo.aiex.web

import org.springframework.boot.context.properties.ConfigurationProperties
import ru.itmo.aiex.common.paging.CursorQuery
import ru.itmo.aiex.common.paging.PageQuery

@ConfigurationProperties("aiex.pagination")
data class PaginationProperties(
    val defaultSize: Int = PageQuery.DEFAULT_SIZE,
    val maxSize: Int = PageQuery.MAX_SIZE,
    val defaultCursorLimit: Int = CursorQuery.DEFAULT_LIMIT,
) {
    init {
        require(maxSize in 1..PageQuery.MAX_SIZE) { "aiex.pagination.max-size должен быть в диапазоне 1..${PageQuery.MAX_SIZE}" }
        require(defaultSize in 1..maxSize) { "aiex.pagination.default-size должен быть в диапазоне 1..$maxSize" }
        require(defaultCursorLimit in 1..maxSize) { "aiex.pagination.default-cursor-limit должен быть в диапазоне 1..$maxSize" }
    }
}
