package ru.itmo.aiex.ingest.service

data class ParseResult(val messageCount: Int, val theirMessageCount: Int, val skippedCount: Int, val theirName: String)
