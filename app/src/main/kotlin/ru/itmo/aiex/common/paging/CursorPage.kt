package ru.itmo.aiex.common.paging

data class CursorPage<T>(val items: List<T>, val nextCursor: String?) {
    fun <R> map(transform: (T) -> R): CursorPage<R> = CursorPage(items.map(transform), nextCursor)

    companion object {
        fun <T> fromOverfetch(rows: List<T>, limit: Int, cursorOf: (T) -> String): CursorPage<T> {
            val hasMore = rows.size > limit
            val items = if (hasMore) rows.subList(0, limit) else rows
            return CursorPage(items.toList(), if (hasMore) cursorOf(items.last()) else null)
        }
    }
}
