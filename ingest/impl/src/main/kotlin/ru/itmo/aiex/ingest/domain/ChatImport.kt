package ru.itmo.aiex.ingest.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_imports", schema = "ingest")
class ChatImport(
    @Id
    val id: UUID,
    @Column(name = "persona_id", nullable = false, updatable = false)
    val personaId: UUID,
    @Column(name = "owner_id", nullable = false, updatable = false)
    val ownerId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    val source: ImportSource,
    @field:NotBlank
    @field:Size(max = FILENAME_MAX)
    @Column(name = "original_filename", nullable = false, updatable = false, length = FILENAME_MAX)
    val originalFilename: String,
    @field:PositiveOrZero
    @Column(name = "size_bytes", nullable = false, updatable = false)
    val sizeBytes: Long,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: ImportStatus = ImportStatus.PENDING
        protected set

    @field:PositiveOrZero
    @Column(name = "message_count", nullable = false)
    var messageCount: Int = 0
        protected set

    @field:PositiveOrZero
    @Column(name = "their_message_count", nullable = false)
    var theirMessageCount: Int = 0
        protected set

    @field:PositiveOrZero
    @Column(name = "skipped_count", nullable = false)
    var skippedCount: Int = 0
        protected set

    @field:Size(max = THEIR_NAME_MAX)
    @Column(name = "their_name", length = THEIR_NAME_MAX)
    var theirName: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code", length = 64)
    var errorCode: ImportErrorCode? = null
        protected set

    @field:Size(max = ERROR_MESSAGE_MAX)
    @Column(name = "error_message", length = ERROR_MESSAGE_MAX)
    var errorMessage: String? = null
        protected set

    @Column(name = "finished_at")
    var finishedAt: Instant? = null
        protected set

    @OneToMany(mappedBy = "chatImport", fetch = FetchType.LAZY)
    var messages: MutableList<ImportedMessage> = mutableListOf()
        protected set

    @Version
    var version: Long? = null
        protected set

    fun startParsing() {
        requireStatus(ImportStatus.PENDING, ImportStatus.PARSING)
        status = ImportStatus.PARSING
    }

    fun markParsed(result: ParseResult, now: Instant) {
        requireStatus(ImportStatus.PARSING, ImportStatus.PARSED)
        status = ImportStatus.PARSED
        messageCount = result.messageCount
        theirMessageCount = result.theirMessageCount
        skippedCount = result.skippedCount
        theirName = result.theirName.take(THEIR_NAME_MAX)
        finishedAt = now
    }

    fun markFailed(code: ImportErrorCode, message: String, now: Instant) {
        if (status == ImportStatus.FAILED) return
        if (status == ImportStatus.PARSED) {
            throw IllegalStateTransitionException(ErrorCode.IMPORT_INVALID_STATE, status.name, ImportStatus.FAILED.name)
        }
        status = ImportStatus.FAILED
        errorCode = code
        errorMessage = message.take(ERROR_MESSAGE_MAX)
        finishedAt = now
    }

    fun markRebuildFailed(message: String) {
        if (status != ImportStatus.PARSED) {
            throw IllegalStateTransitionException(ErrorCode.IMPORT_INVALID_STATE, status.name, ImportStatus.PARSED.name)
        }
        errorCode = ImportErrorCode.PROFILE_REBUILD_FAILED
        errorMessage = message.take(ERROR_MESSAGE_MAX)
    }

    private fun requireStatus(expected: ImportStatus, target: ImportStatus) {
        if (status != expected) throw IllegalStateTransitionException(ErrorCode.IMPORT_INVALID_STATE, status.name, target.name)
    }

    override fun equals(other: Any?): Boolean = this === other || (other is ChatImport && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val FILENAME_MAX = 255
        const val THEIR_NAME_MAX = 128
        const val ERROR_MESSAGE_MAX = 500
    }
}
