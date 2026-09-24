package ru.itmo.aiex.care.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.time.temporal.ChronoUnit

class SlotScheduleTest {
    private val base = Instant.parse("2026-10-01T10:00:00Z")

    private fun at(minutes: Long) = base.plus(minutes, ChronoUnit.MINUTES)

    @ParameterizedTest(name = "[{0}, +{1}) и [{2}, +{3}) пересекаются: {4}")
    @CsvSource(
        "0, 60, 0, 60, true",
        "0, 60, 30, 60, true",
        "0, 60, -30, 60, true",
        "0, 60, 15, 15, true",
        "0, 60, -60, 240, true",
        "0, 60, 60, 30, false",
        "0, 60, -30, 30, false",
        "0, 60, 90, 15, false",
        "0, 60, -240, 120, false",
    )
    fun `полуинтервалы пересекаются, только если каждый начинается раньше конца другого`(
        aStart: Long,
        aDuration: Int,
        bStart: Long,
        bDuration: Int,
        expected: Boolean,
    ) {
        assertThat(SlotSchedule.overlaps(at(aStart), aDuration, at(bStart), bDuration)).isEqualTo(expected)
        assertThat(SlotSchedule.overlaps(at(bStart), bDuration, at(aStart), aDuration)).describedAs("симметрия").isEqualTo(expected)
    }

    @org.junit.jupiter.api.Test
    fun `окно поиска кандидатов покрывает самый длинный слот, который ещё может задеть новый`() {
        val start = at(0)
        val from = SlotSchedule.candidatesFrom(start)
        assertThat(from).isEqualTo(at(-SlotSchedule.MAX_DURATION_MIN.toLong()))

        assertThat(SlotSchedule.overlaps(from.plus(1, ChronoUnit.MINUTES), SlotSchedule.MAX_DURATION_MIN, start, 15)).isTrue()

        assertThat(SlotSchedule.overlaps(from, SlotSchedule.MAX_DURATION_MIN, start, 15)).isFalse()
        assertThat(SlotSchedule.end(start, 90)).isEqualTo(at(90))
    }
}
