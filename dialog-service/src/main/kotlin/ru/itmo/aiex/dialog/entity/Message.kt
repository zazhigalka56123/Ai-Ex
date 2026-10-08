package ru.itmo.aiex.dialog.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.id.Ids
import ru.itmo.aiex.common.paging.TimeIdPosition
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "messages", schema = "dialog")
class Message(
    @Id
    val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, updatable = false)
    val conversation: Conversation,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24, updatable = false)
    val sender: MessageSender,
    @field:NotBlank
    @field:Size(max = MAX_BODY_LENGTH)
    @Column(nullable = false, columnDefinition = "text", updatable = false)
    val body: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "agent_run_id", updatable = false)
    val agentRunId: UUID? = null,
) {
    @Column(name = "conversation_id", insertable = false, updatable = false)
    val conversationId: UUID = conversation.id

    @Column(nullable = false)
    var flagged: Boolean = false
        protected set

    val position: TimeIdPosition get() = TimeIdPosition(createdAt, id)

    fun flag(): Boolean {
        if (flagged) return false
        flagged = true
        return true
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Message && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val MAX_BODY_LENGTH = 4000

        fun fromUser(conversation: Conversation, text: String, now: Instant) = Message(Ids.next(), conversation, MessageSender.USER, text, now)

        fun fromPersona(conversation: Conversation, text: String, now: Instant, agentRunId: UUID) =
            Message(Ids.next(), conversation, MessageSender.PERSONA, text, now, agentRunId)
    }
}
