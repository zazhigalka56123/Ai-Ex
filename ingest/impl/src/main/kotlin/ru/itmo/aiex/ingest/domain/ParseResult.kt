package ru.itmo.aiex.ingest.domain

data class ParseResult(val messageCount: Int, val theirMessageCount: Int, val skippedCount: Int, val theirName: String)
