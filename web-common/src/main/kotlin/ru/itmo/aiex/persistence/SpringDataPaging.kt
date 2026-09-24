package ru.itmo.aiex.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.paging.PageView
import ru.itmo.aiex.common.paging.SortDirection

fun PageQuery.toPageable(propertyMapping: Map<String, String> = emptyMap()): Pageable {
    val orders =
        sort.map { order ->
            val property = propertyMapping[order.property] ?: order.property
            if (order.direction == SortDirection.ASC) Sort.Order.asc(property) else Sort.Order.desc(property)
        }
    return PageRequest.of(page, size, Sort.by(orders))
}

fun <T : Any> Page<T>.toPageView(): PageView<T> = PageView(content, number, size, totalElements)
