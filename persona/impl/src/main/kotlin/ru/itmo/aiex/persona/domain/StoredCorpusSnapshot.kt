package ru.itmo.aiex.persona.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "corpus_snapshots", schema = "persona")
class StoredCorpusSnapshot(
    @Id
    val id: UUID,
    @Column(name = "persona_id", nullable = false, updatable = false)
    val personaId: UUID,
    @Column(name = "import_id", nullable = false, updatable = false)
    val importId: UUID,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    val payload: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is StoredCorpusSnapshot && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
