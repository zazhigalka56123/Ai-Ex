package ru.itmo.aiex.llm

data class LlmResponse(val text: String, val model: String, val tokensIn: Int, val tokensOut: Int) {
    override fun toString(): String = "LlmResponse(model=$model, chars=${text.length}, tokensIn=$tokensIn, tokensOut=$tokensOut)"
}
