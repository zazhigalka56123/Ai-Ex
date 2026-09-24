package ru.itmo.aiex.iam.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.itmo.aiex.common.security.RoleCode

@Entity
@Table(name = "roles", schema = "iam")
class Role(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 24)
    val code: RoleCode,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    override fun equals(other: Any?): Boolean = this === other || (other is Role && other.code == code)

    override fun hashCode(): Int = code.hashCode()
}
