package ru.itmo.aiex.iam.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import ru.itmo.aiex.common.security.RoleCode
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users", schema = "iam")
class User(
    @Id
    val id: UUID,
    @field:NotBlank
    @field:Email
    @field:Size(max = 254)
    @Column(nullable = false, unique = true, length = 254)
    var email: String,
    @field:NotBlank
    @field:Size(max = 64)
    @Column(name = "display_name", nullable = false, length = 64)
    var displayName: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: UserStatus = UserStatus.ACTIVE
        protected set

    @field:NotEmpty
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        schema = "iam",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    var roles: MutableSet<Role> = mutableSetOf()
        protected set

    @Version
    var version: Long? = null
        protected set

    val roleCodes: Set<RoleCode> get() = roles.map { it.code }.toSet()

    val isActive: Boolean get() = status == UserStatus.ACTIVE

    fun replaceRoles(newRoles: Collection<Role>, now: Instant) {
        require(newRoles.isNotEmpty()) { "У пользователя должна быть хотя бы одна роль" }
        roles.clear()
        roles.addAll(newRoles)
        updatedAt = now
    }

    fun rename(newDisplayName: String, now: Instant) {
        displayName = newDisplayName
        updatedAt = now
    }

    fun changeStatus(newStatus: UserStatus, now: Instant) {
        if (status == newStatus) return
        status = newStatus
        updatedAt = now
    }

    override fun equals(other: Any?): Boolean = this === other || (other is User && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
