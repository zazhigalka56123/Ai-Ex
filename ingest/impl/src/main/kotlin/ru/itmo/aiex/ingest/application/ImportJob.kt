package ru.itmo.aiex.ingest.application

import ru.itmo.aiex.ingest.domain.ImportSource
import java.util.UUID

class ImportJob(
    val importId: UUID,
    val personaId: UUID,
    val ownerId: UUID,
    val personaName: String,
    val source: ImportSource,
    val theirName: String?,
    val content: ByteArray,
) {
    override fun toString(): String = "ImportJob(importId=$importId, personaId=$personaId, source=$source, bytes=${content.size})"
}
