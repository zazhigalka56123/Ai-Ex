package ru.itmo.aiex.persona.domain

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
@Table(name = "tags", schema = "persona")
class Tag(code: String, title: String) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @field:NotBlank
    @field:Size(max = CODE_MAX)
    @field:Pattern(regexp = CODE_PATTERN)
    @Column(nullable = false, unique = true, length = CODE_MAX)
    var code: String = code
        protected set

    @field:NotBlank
    @field:Size(max = TITLE_MAX)
    @Column(nullable = false, length = TITLE_MAX)
    var title: String = title
        protected set

    fun rename(newTitle: String) {
        title = newTitle
    }

    override fun equals(other: Any?): Boolean = this === other || (other is Tag && other.code == code)

    override fun hashCode(): Int = code.hashCode()

    companion object {
        const val CODE_MAX = 48
        const val TITLE_MAX = 96
        const val CODE_PATTERN = "^[a-z0-9-]+$"
    }
}
