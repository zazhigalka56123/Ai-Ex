package ru.itmo.aiex.agent.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "agent_runs", schema = "agent")
class AgentRun(
    @Id
    val id: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    val kind: AgentRunKind,
    @Column(name = "conversation_id", updatable = false)
    val conversationId: UUID?,
    @Column(name = "persona_id", nullable = false, updatable = false)
    val personaId: UUID,
    @Column(name = "persona_profile_id", updatable = false)
    val personaProfileId: UUID?,
    model: String,
    @field:Pattern(regexp = "[0-9a-f]{64}")
    @Column(name = "prompt_hash", nullable = false, length = 64, updatable = false)
    val promptHash: String,
    @field:Size(max = PROMPT_PREVIEW_LENGTH)
    @Column(name = "prompt_preview", nullable = false, length = PROMPT_PREVIEW_LENGTH, updatable = false)
    val promptPreview: String,
    @field:PositiveOrZero
    @Column(name = "history_size", nullable = false, updatable = false)
    val historySize: Int,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @field:NotBlank
    @field:Size(max = 128)
    @Column(nullable = false, length = 128)
    var model: String = model
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: AgentRunStatus = AgentRunStatus.PENDING
        protected set

    @field:PositiveOrZero
    @Column(name = "latency_ms")
    var latencyMs: Int? = null
        protected set

    @field:PositiveOrZero
    @Column(name = "tokens_in")
    var tokensIn: Int? = null
        protected set

    @field:PositiveOrZero
    @Column(name = "tokens_out")
    var tokensOut: Int? = null
        protected set

    @field:Size(max = 64)
    @Column(name = "error_code", length = 64)
    var errorCode: String? = null
        protected set

    @Column(name = "finished_at")
    var finishedAt: Instant? = null
        protected set

    fun succeed(actualModel: String, latencyMs: Int, tokensIn: Int, tokensOut: Int, now: Instant) {
        requirePending()
        model = actualModel.take(MODEL_LENGTH)
        this.latencyMs = latencyMs
        this.tokensIn = tokensIn
        this.tokensOut = tokensOut
        status = AgentRunStatus.SUCCESS
        finishedAt = now
    }

    fun fail(failure: AgentRunStatus, errorCode: String, latencyMs: Int, now: Instant) {
        require(failure == AgentRunStatus.FAILED || failure == AgentRunStatus.TIMEOUT) {
            "Неуспешный статус прогона - FAILED или TIMEOUT, а не $failure"
        }
        requirePending()
        this.errorCode = errorCode.take(ERROR_CODE_LENGTH)
        this.latencyMs = latencyMs
        status = failure
        finishedAt = now
    }

    private fun requirePending() = check(status == AgentRunStatus.PENDING) { "Прогон $id уже завершён со статусом $status" }

    override fun equals(other: Any?): Boolean = this === other || (other is AgentRun && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val PROMPT_PREVIEW_LENGTH = 200
        private const val MODEL_LENGTH = 128
        private const val ERROR_CODE_LENGTH = 64
    }
}
