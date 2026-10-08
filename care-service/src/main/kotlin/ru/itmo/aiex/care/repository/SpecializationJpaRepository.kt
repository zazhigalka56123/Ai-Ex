package ru.itmo.aiex.care.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.itmo.aiex.care.entity.Specialization
internal interface SpecializationJpaRepository : JpaRepository<Specialization, Long> {
    fun findAllByCodeIn(codes: Collection<String>): List<Specialization>

    fun existsByCode(code: String): Boolean
}
