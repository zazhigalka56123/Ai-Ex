package ru.itmo.aiex.care.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.care.domain.Specialization

internal interface SpecializationJpaRepository : JpaRepository<Specialization, Long> {
    fun findAllByCodeIn(codes: Collection<String>): List<Specialization>

    fun existsByCode(code: String): Boolean
}
