package ru.itmo.aiex.care.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateSpecialistRequest(
    @field:NotBlank
    @field:Size(max = 128)
    @field:Schema(example = "Психолог, помогаю пережить расставание")
    val headline: String,
    @field:NotBlank
    @field:Size(max = 4000)
    @field:Schema(example = "Работаю в когнитивно-поведенческом подходе, стаж 7 лет.")
    val bio: String,
    @field:DecimalMin("0.00")
    @field:Digits(integer = 8, fraction = 2)
    @field:Schema(example = "2500.00")
    val pricePerHour: BigDecimal,
    @field:Size(min = 1, max = 10)
    @field:Schema(description = "Коды из справочника специализаций, например `breakup`, `relationships`")
    val specializationCodes: Set<String>,
)
