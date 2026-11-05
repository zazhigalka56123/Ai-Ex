package ru.itmo.aiex.common.paging

data class PageQuery(val page: Int, val size: Int, val sort: List<SortOrder> = emptyList()) {
    init {
        require(page >= 0) { "page должен быть ≥ 0" }
        require(size >= 1) { "size должен быть ≥ 1" }
    }

    val offset: Long get() = page.toLong() * size

    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 50
    }
}
