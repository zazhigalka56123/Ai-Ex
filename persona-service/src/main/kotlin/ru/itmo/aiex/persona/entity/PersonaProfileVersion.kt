package ru.itmo.aiex.persona.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "persona_profile_versions", schema = "persona")
class PersonaProfileVersion(
    @Id
    val id: UUID,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "persona_id", nullable = false, updatable = false)
    val persona: Persona,
    @field:Positive
    @Column(name = "version_no", nullable = false, updatable = false)
    val versionNo: Int,
    @field:NotBlank
    @Column(name = "system_prompt", nullable = false, updatable = false, columnDefinition = "text")
    val systemPrompt: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    val style: String,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "corpus_stats", nullable = false, updatable = false, columnDefinition = "jsonb")
    val corpusStats: String,
    @Column(name = "corpus_snapshot_id", nullable = false, updatable = false)
    val corpusSnapshotId: UUID,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Column(nullable = false)
    var active: Boolean = true
        protected set

    override fun equals(other: Any?): Boolean = this === other || (other is PersonaProfileVersion && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
