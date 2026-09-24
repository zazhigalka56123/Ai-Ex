package ru.itmo.aiex.dialog.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "conversations", schema = "dialog")
class Conversation(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,
    @Column(name = "persona_id", nullable = false, updatable = false)
    val personaId: UUID,
    title: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @field:NotBlank
    @field:Size(max = TITLE_MAX_LENGTH)
    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    var title: String = title
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: ConversationStatus = ConversationStatus.ACTIVE
        protected set

    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null
        protected set

    @field:PositiveOrZero
    @Column(name = "message_count", nullable = false)
    var messageCount: Int = 0
        protected set

    @OneToMany(mappedBy = "conversation", fetch = FetchType.LAZY)
    @OrderBy("createdAt DESC, id DESC")
    var messages: MutableList<Message> = mutableListOf()
        protected set

    @Version
    var version: Long? = null
        protected set

    val isActive: Boolean get() = status == ConversationStatus.ACTIVE

    fun isOwnedBy(userId: UUID): Boolean = this.userId == userId

    fun archive(): Boolean {
        if (status == ConversationStatus.ARCHIVED) return false
        status = ConversationStatus.ARCHIVED
        return true
    }

    fun ensureAcceptsMessages() {
        if (!isActive) throw ConflictException(ErrorCode.CONVERSATION_INVALID_STATE, "Беседа $id в архиве: новые сообщения не принимаются")
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Conversation && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val TITLE_MAX_LENGTH = 128

        fun defaultTitle(personaName: String): String = "Беседа с $personaName".take(TITLE_MAX_LENGTH)
    }
}
