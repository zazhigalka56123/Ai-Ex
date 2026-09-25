package ru.itmo.aiex.persona.dto

import java.time.Instant
import java.util.UUID

data class CorpusSnapshot(
    val importId: UUID,
    val source: String,
    val theirName: String?,
    val totalMessages: Int,
    val theirMessages: Int,
    val myMessages: Int,
    val attachments: Int,
    val periodStart: Instant?,
    val periodEnd: Instant?,
    val stats: CorpusStats,
    val samplePhrases: List<String>,
)
