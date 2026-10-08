package ru.itmo.aiex.common.paging

data class CursorQuery(val cursor: String?, val limit: Int) {
    init {
        require(limit >= 1) { "limit должен быть ≥ 1" }
    }

    companion object {
        const val DEFAULT_LIMIT = 30
    }
}
