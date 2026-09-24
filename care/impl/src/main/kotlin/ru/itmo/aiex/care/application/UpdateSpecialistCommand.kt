package ru.itmo.aiex.care.application

import ru.itmo.aiex.care.domain.SpecialistStatus
import java.math.BigDecimal

data class UpdateSpecialistCommand(
    val headline: String? = null,
    val bio: String? = null,
    val pricePerHour: BigDecimal? = null,
    val status: SpecialistStatus? = null,
    val specializationCodes: Set<String>? = null,
)
