package ru.itmo.aiex.care.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

@Entity
@Table(name = "specializations", schema = "care")
class Specialization(
    @field:NotBlank
    @field:Size(max = CODE_MAX_LENGTH)
    @field:Pattern(regexp = CODE_PATTERN)
    @Column(nullable = false, unique = true, length = CODE_MAX_LENGTH, updatable = false)
    val code: String,
    title: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @field:NotBlank
    @field:Size(max = TITLE_MAX_LENGTH)
    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    var title: String = title
        protected set

    fun rename(newTitle: String) {
        title = newTitle
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Specialization && other.code == code)

    override fun hashCode(): Int = code.hashCode()

    companion object {
        const val CODE_PATTERN = "^[a-z0-9-]+$"
        const val CODE_MAX_LENGTH = 48
        const val TITLE_MAX_LENGTH = 96
    }
}
