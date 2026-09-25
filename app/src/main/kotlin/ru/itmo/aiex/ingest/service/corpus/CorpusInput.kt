package ru.itmo.aiex.ingest.service.corpus

import ru.itmo.aiex.ingest.entity.ImportSource
import java.util.UUID

data class CorpusInput(val importId: UUID, val source: ImportSource, val theirName: String, val messages: List<CorpusMessage>, val attachments: Int)
