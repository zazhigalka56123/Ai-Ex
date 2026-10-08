package ru.itmo.aiex.ingest.dto

import io.swagger.v3.oas.annotations.media.Schema
import ru.itmo.aiex.ingest.entity.ChatImport
import ru.itmo.aiex.ingest.entity.ImportErrorCode
import ru.itmo.aiex.ingest.entity.ImportSource
import ru.itmo.aiex.ingest.entity.ImportStatus
import java.time.Instant
import java.util.UUID

data class ImportResponse(
    val id: UUID,
    val personaId: UUID,
    val source: ImportSource,
    val status: ImportStatus,
    val originalFilename: String,
    val sizeBytes: Long,
    @field:Schema(description = "Сколько текстовых сообщений сохранено")
    val messageCount: Int,
    @field:Schema(description = "Из них - сообщений персоны («их» стороны)")
    val theirMessageCount: Int,
    @field:Schema(description = "Отброшено: служебные записи, пустые, удалённые, вложения без текста")
    val skippedCount: Int,
    @field:Schema(description = "Каким автором выгрузки оказалась персона")
    val theirName: String?,
    @field:Schema(description = "Причина неудачи; `PROFILE_REBUILD_FAILED` при статусе `PARSED` - сообщения сохранены, профиль не собран")
    val errorCode: ImportErrorCode?,
    val errorMessage: String?,
    val createdAt: Instant,
    val finishedAt: Instant?,
)

fun ChatImport.toResponse() = ImportResponse(
    id = id,
    personaId = personaId,
    source = source,
    status = status,
    originalFilename = originalFilename,
    sizeBytes = sizeBytes,
    messageCount = messageCount,
    theirMessageCount = theirMessageCount,
    skippedCount = skippedCount,
    theirName = theirName,
    errorCode = errorCode,
    errorMessage = errorMessage,
    createdAt = createdAt,
    finishedAt = finishedAt,
)
