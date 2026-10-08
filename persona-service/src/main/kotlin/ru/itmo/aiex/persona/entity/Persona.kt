package ru.itmo.aiex.persona.entity

import jakarta.persistence.CascadeType
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
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.error.ConflictException
import ru.itmo.aiex.common.error.ErrorCode
import ru.itmo.aiex.common.error.IllegalStateTransitionException
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "personas", schema = "persona")
class Persona(
    @Id
    val id: UUID,
    @Column(name = "owner_id", nullable = false, updatable = false)
    val ownerId: UUID,
    name: String,
    relationshipKind: RelationshipKind,
    description: String?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @field:NotBlank
    @field:Size(max = NAME_MAX)
    @Column(nullable = false, length = NAME_MAX)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_kind", nullable = false, length = 24)
    var relationshipKind: RelationshipKind = relationshipKind
        protected set

    @field:Size(max = DESCRIPTION_MAX)
    @Column(length = DESCRIPTION_MAX)
    var description: String? = description
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: PersonaStatus = PersonaStatus.DRAFT
        protected set

    @Column(name = "active_profile_id")
    var activeProfileId: UUID? = null
        protected set

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    @Column(name = "archived_at")
    var archivedAt: Instant? = null
        protected set

    @OneToMany(mappedBy = "persona", fetch = FetchType.LAZY)
    @OrderBy("traitKey ASC")
    var traits: MutableList<PersonaTrait> = mutableListOf()
        protected set

    @OneToMany(mappedBy = "persona", fetch = FetchType.LAZY)
    @OrderBy("versionNo DESC")
    var profileVersions: MutableList<PersonaProfileVersion> = mutableListOf()
        protected set

    @OneToMany(mappedBy = "persona", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var tags: MutableSet<PersonaTag> = mutableSetOf()
        protected set

    @Version
    var version: Long? = null
        protected set

    val isArchived: Boolean get() = status == PersonaStatus.ARCHIVED

    fun isOwnedBy(userId: UUID): Boolean = ownerId == userId

    fun ensureEditable() {
        if (isArchived) throw ConflictException(ErrorCode.PERSONA_INVALID_STATE, "Персона архивирована, изменить её нельзя")
    }

    fun edit(newName: String?, newKind: RelationshipKind?, newDescription: String?, clearDescription: Boolean, now: Instant) {
        ensureEditable()
        newName?.let { name = it }
        newKind?.let { relationshipKind = it }
        if (clearDescription) description = null else newDescription?.let { description = it }
        updatedAt = now
    }

    fun touch(now: Instant) {
        updatedAt = now
    }

    fun startTraining(now: Instant) {
        transitionTo(PersonaStatus.TRAINING, now)
    }

    fun activateProfile(profileId: UUID, now: Instant) {
        if (status != PersonaStatus.TRAINING) {
            throw IllegalStateTransitionException(ErrorCode.PERSONA_INVALID_STATE, status.name, PersonaStatus.READY.name)
        }
        transitionTo(PersonaStatus.READY, now)
        activeProfileId = profileId
    }

    fun trainingFailed(now: Instant): Boolean {
        if (status != PersonaStatus.TRAINING) return false
        transitionTo(if (activeProfileId != null) PersonaStatus.READY else PersonaStatus.DRAFT, now)
        return true
    }

    fun archive(now: Instant): Boolean {
        if (isArchived) return false
        transitionTo(PersonaStatus.ARCHIVED, now)
        archivedAt = now
        activeProfileId = null
        return true
    }

    private fun transitionTo(target: PersonaStatus, now: Instant) {
        if (!status.canTransitionTo(target)) {
            throw IllegalStateTransitionException(ErrorCode.PERSONA_INVALID_STATE, status.name, target.name)
        }
        status = target
        updatedAt = now
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Persona && other.id == id)

    override fun hashCode(): Int = id.hashCode()

    companion object {
        const val NAME_MAX = 64
        const val DESCRIPTION_MAX = 500
    }
}
