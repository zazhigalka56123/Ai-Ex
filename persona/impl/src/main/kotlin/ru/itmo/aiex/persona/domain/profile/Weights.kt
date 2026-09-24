package ru.itmo.aiex.persona.domain.profile

import java.math.BigDecimal
import java.math.RoundingMode

fun Double.toWeight(): BigDecimal = BigDecimal.valueOf(clamp01()).setScale(WEIGHT_SCALE, RoundingMode.HALF_UP)

val FULL_WEIGHT: BigDecimal = BigDecimal.ONE.setScale(WEIGHT_SCALE)

private const val WEIGHT_SCALE = 3

internal fun Double.clamp01(): Double = if (isNaN()) 0.0 else coerceIn(0.0, 1.0)
