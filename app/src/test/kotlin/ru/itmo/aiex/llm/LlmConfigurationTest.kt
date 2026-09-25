package ru.itmo.aiex.llm

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.client.RestClient

class LlmConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withUserConfiguration(LlmConfiguration::class.java)
            .withBean(RestClient.Builder::class.java, { RestClient.builder() })

    @Test
    fun `по умолчанию - детерминированная заглушка`() {
        runner.run { context -> assertThat(context.getBean(LlmClient::class.java)).isInstanceOf(StubLlmClient::class.java) }
    }

    @Test
    fun `openai и ollama выбираются переменной среды`() {
        runner
            .withPropertyValues("aiex.llm.provider=openai", "aiex.llm.model=gpt-test", "aiex.llm.api-key=sk-test")
            .run { context ->
                val client = context.getBean(LlmClient::class.java)
                assertThat(client).isInstanceOf(OpenAiCompatibleLlmClient::class.java)
                assertThat(client.model).isEqualTo("gpt-test")
            }
        runner
            .withPropertyValues("aiex.llm.provider=ollama", "aiex.llm.model=llama3.1")
            .run { context -> assertThat(context.getBean(LlmClient::class.java)).isInstanceOf(OllamaLlmClient::class.java) }
    }

    @Test
    fun `без модели реальный провайдер не стартует`() {
        runner.withPropertyValues("aiex.llm.provider=openai").run { context -> assertThat(context).hasFailed() }
        runner.withPropertyValues("aiex.llm.provider=openai", "aiex.llm.model=", "aiex.llm.base-url=").run { context ->
            assertThat(context).hasFailed()
        }
    }
}
