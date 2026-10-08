package ru.itmo.aiex.agent.service

fun String.truncateSafely(maxLength: Int): String {
    if (length <= maxLength) return this
    val end = if (Character.isHighSurrogate(this[maxLength - 1])) maxLength - 1 else maxLength
    return substring(0, end)
}
