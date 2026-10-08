package ru.itmo.aiex.llm

class OllamaLlmClient(delegate: OpenAiCompatibleLlmClient) : LlmClient by delegate
