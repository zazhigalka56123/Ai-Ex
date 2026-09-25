package ru.itmo.aiex.ingest.service

import ru.itmo.aiex.ingest.entity.ImportSource
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
