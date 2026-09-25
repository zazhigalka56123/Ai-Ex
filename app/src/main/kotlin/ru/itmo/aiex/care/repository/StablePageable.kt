package ru.itmo.aiex.care.repository

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import ru.itmo.aiex.common.paging.PageQuery
import ru.itmo.aiex.common.persistence.toPageable
internal fun PageQuery.toStablePageable(): Pageable {
    val pageable = toPageable()
    return PageRequest.of(pageable.pageNumber, pageable.pageSize, pageable.sort.and(Sort.by("id")))
}
