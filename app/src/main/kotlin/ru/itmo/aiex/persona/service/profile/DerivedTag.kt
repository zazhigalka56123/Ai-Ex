package ru.itmo.aiex.persona.service.profile

data class DerivedTag(val code: String, val weight: Double) {
    init {
        require(weight in 0.0..1.0) { "Вес тега $code вне диапазона 0..1: $weight" }
    }
}
