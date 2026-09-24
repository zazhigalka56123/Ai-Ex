package ru.itmo.aiex.care.application

import java.math.BigDecimal

data class CreateSpecialistCommand(val headline: String, val bio: String, val pricePerHour: BigDecimal, val specializationCodes: Set<String>)
