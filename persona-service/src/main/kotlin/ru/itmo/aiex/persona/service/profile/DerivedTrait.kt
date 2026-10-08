package ru.itmo.aiex.persona.service.profile

data class DerivedTrait(val key: String, val value: String, val weight: Double) {
    init {
        require(weight in 0.0..1.0) { "Вес черты $key вне диапазона 0..1: $weight" }
    }
}
