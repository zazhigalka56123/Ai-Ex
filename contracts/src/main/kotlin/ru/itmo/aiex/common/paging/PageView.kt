package ru.itmo.aiex.common.paging

data class PageView<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long) {
    val totalPages: Int get() = if (totalElements == 0L) 0 else ((totalElements + size - 1) / size).toInt()

    val hasNext: Boolean get() = page + 1 < totalPages

    val hasPrevious: Boolean get() = page > 0

    fun <R> map(transform: (T) -> R): PageView<R> = PageView(items.map(transform), page, size, totalElements)
}
