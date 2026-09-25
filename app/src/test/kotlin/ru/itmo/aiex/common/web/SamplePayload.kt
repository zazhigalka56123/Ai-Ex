package ru.itmo.aiex.common.web

import jakarta.validation.constraints.NotBlank

data class SamplePayload(@field:NotBlank val name: String, val count: Int)
