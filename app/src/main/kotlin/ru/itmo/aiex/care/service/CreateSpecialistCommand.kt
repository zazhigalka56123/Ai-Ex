package ru.itmo.aiex.care.service

import java.math.BigDecimal

data class CreateSpecialistCommand(val headline: String, val bio: String, val pricePerHour: BigDecimal, val specializationCodes: Set<String>)
