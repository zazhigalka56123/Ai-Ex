package ru.itmo.aiex.ingest.domain.corpus

import ru.itmo.aiex.ingest.domain.ImportSource
import java.util.UUID

data class CorpusInput(val importId: UUID, val source: ImportSource, val theirName: String, val messages: List<CorpusMessage>, val attachments: Int)
