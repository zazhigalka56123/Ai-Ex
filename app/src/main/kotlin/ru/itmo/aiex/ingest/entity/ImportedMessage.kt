package ru.itmo.aiex.ingest.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "imported_messages", schema = "ingest")
class ImportedMessage(
    @Id
    val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false, updatable = false)
    val chatImport: ChatImport,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    val author: MessageAuthor,
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    val body: String,
    @Column(name = "sent_at", updatable = false)
    val sentAt: Instant?,
    @field:PositiveOrZero
    @Column(nullable = false, updatable = false)
    val ordinal: Int,
) {
    override fun toString(): String = "ImportedMessage(id=$id, ordinal=$ordinal, author=$author)"

    override fun equals(other: Any?): Boolean = this === other || (other is ImportedMessage && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
