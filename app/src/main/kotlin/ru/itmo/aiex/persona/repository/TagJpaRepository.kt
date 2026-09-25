package ru.itmo.aiex.persona.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.persona.entity.Tag
internal interface TagJpaRepository : JpaRepository<Tag, Long> {
    fun findAllByCodeIn(codes: Collection<String>): List<Tag>

    fun existsByCode(code: String): Boolean
}
