package ru.itmo.aiex.care.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Size
import ru.itmo.aiex.care.entity.SpecialistStatus
import java.math.BigDecimal

data class UpdateSpecialistRequest(
    @field:Size(min = 1, max = 128)
    val headline: String? = null,
    @field:Size(min = 1, max = 4000)
    val bio: String? = null,
    @field:DecimalMin("0.00")
    @field:Digits(integer = 8, fraction = 2)
    val pricePerHour: BigDecimal? = null,
    @field:Schema(description = "`INACTIVE` скрывает профиль из каталога")
    val status: SpecialistStatus? = null,
    @field:Size(min = 1, max = 10)
    @field:Schema(description = "Полная замена набора специализаций")
    val specializationCodes: Set<String>? = null,
)
