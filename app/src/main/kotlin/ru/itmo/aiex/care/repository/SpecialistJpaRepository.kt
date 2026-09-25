package ru.itmo.aiex.care.repository

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.itmo.aiex.care.entity.Specialist
import ru.itmo.aiex.care.entity.SpecialistStatus
import java.util.UUID

internal interface SpecialistJpaRepository : JpaRepository<Specialist, UUID> {
    fun existsByUserId(userId: UUID): Boolean

    fun countByStatus(status: SpecialistStatus): Long

    fun findAllByStatus(status: SpecialistStatus, pageable: Pageable): Page<Specialist>

    @Query(
        value = """
            select s from Specialist s
            where s.status = :status
              and exists (select 1 from Specialist f join f.specializations sp where f = s and sp.code = :code)
        """,
        countQuery = """
            select count(s) from Specialist s
            where s.status = :status
              and exists (select 1 from Specialist f join f.specializations sp where f = s and sp.code = :code)
        """,
    )
    fun findAllByStatusAndSpecialization(
        @Param("status") status: SpecialistStatus,
        @Param("code") code: String,
        pageable: Pageable,
    ): Page<Specialist>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Specialist s where s.id = :id")
    fun findLockedById(@Param("id") id: UUID): Specialist?

    @Modifying
    @Query(value = "UPDATE care.specialists SET booked_count = booked_count + 1 WHERE id = :id", nativeQuery = true)
    fun incrementBookedCount(@Param("id") id: UUID): Int

    @Modifying
    @Query(value = "UPDATE care.specialists SET booked_count = booked_count - 1 WHERE id = :id AND booked_count > 0", nativeQuery = true)
    fun decrementBookedCount(@Param("id") id: UUID): Int

    fun existsBySpecializationsId(specializationId: Long): Boolean
}
