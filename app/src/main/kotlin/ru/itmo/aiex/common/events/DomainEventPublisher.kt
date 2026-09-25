package ru.itmo.aiex.common.events

fun interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
