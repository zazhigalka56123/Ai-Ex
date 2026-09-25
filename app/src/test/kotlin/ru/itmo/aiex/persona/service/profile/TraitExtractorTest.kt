package ru.itmo.aiex.persona.service.profile

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.itmo.aiex.persona.testing.TestCorpus
class TraitExtractorTest {
    private val extractor = TraitExtractor()

    private fun traits(stats: ru.itmo.aiex.persona.dto.CorpusStats, theirMessages: Int = 30) =
        extractor.extract(stats, theirMessages).associateBy { it.key }

    @Test
    fun `холодный корпус - короткие медленные ответы, ночь, ревность`() {
        val traits = traits(TestCorpus.coldStats())
        assertThat(traits.keys).containsExactlyInAnyOrder(
            "reply_speed",
            "message_length",
            "emoji_usage",
            "caps",
            "lowercase_start",
            "night_owl",
            "jealousy",
            "affection",
            "initiative",
            "questions",
        )
        assertThat(traits.getValue("reply_speed").value).isEqualTo("slow")
        assertThat(traits.getValue("reply_speed").weight).isCloseTo(4000.0 / 7200, within(1e-9))
        assertThat(traits.getValue("message_length").value).isEqualTo("short")
        assertThat(traits.getValue("emoji_usage").value).isEqualTo("low")
        assertThat(traits.getValue("caps").value).isEqualTo("often")
        assertThat(traits.getValue("caps").weight).isEqualTo(0.3)
        assertThat(traits.getValue("lowercase_start").value).isEqualTo("yes")
        assertThat(traits.getValue("night_owl").value).isEqualTo("yes")

        assertThat(traits.getValue("jealousy").value).isEqualTo("high")
        assertThat(traits.getValue("jealousy").weight).isEqualTo(1.0)
        assertThat(traits.getValue("affection").value).isEqualTo("low")
        assertThat(traits.getValue("affection").weight).isEqualTo(0.0)
        assertThat(traits.getValue("initiative").value).isEqualTo("low")
        assertThat(traits.getValue("questions").value).isEqualTo("sometimes")
        assertThat(traits.values).allSatisfy { assertThat(it.weight).isBetween(0.0, 1.0) }
    }

    @Test
    fun `тёплый корпус - быстрые длинные ответы, много эмодзи и нежности`() {
        val traits = traits(TestCorpus.warmStats())
        assertThat(traits.getValue("reply_speed").value).isEqualTo("fast")
        assertThat(traits.getValue("message_length").value).isEqualTo("long")
        assertThat(traits.getValue("emoji_usage").value).isEqualTo("high")
        assertThat(traits.getValue("emoji_usage").weight).isEqualTo(1.0)
        assertThat(traits.getValue("caps").value).isEqualTo("rare")
        assertThat(traits.getValue("night_owl").value).isEqualTo("no")
        assertThat(traits.getValue("affection").value).isEqualTo("high")
        assertThat(traits.getValue("jealousy").value).isEqualTo("low")
        assertThat(traits.getValue("initiative").value).isEqualTo("high")
    }

    @Test
    fun `без таймстемпов скорость ответа неизвестна и черта не выводится`() {
        val traits = traits(TestCorpus.coldStats().copy(avgReplyDelaySeconds = null))
        assertThat(traits).doesNotContainKey("reply_speed")
        assertThat(ReplySpeed.of(null)).isEqualTo(ReplySpeed.UNKNOWN)
    }

    @ParameterizedTest
    @CsvSource("0, fast", "119, fast", "120, medium", "1799, medium", "1800, slow", "86400, slow")
    fun `пороги скорости ответа`(seconds: Long, expected: String) {
        assertThat(ReplySpeed.of(seconds).code).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource("24.9, short", "25, medium", "80, medium", "80.1, long")
    fun `пороги длины сообщения`(avg: Double, expected: String) {
        assertThat(traits(TestCorpus.coldStats().copy(avgMessageLength = avg)).getValue("message_length").value).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource("0.0, none", "0.04, none", "0.05, low", "0.5, low", "0.51, high")
    fun `пороги эмодзи`(perMessage: Double, expected: String) {
        assertThat(traits(TestCorpus.coldStats().copy(emojiPerMessage = perMessage)).getValue("emoji_usage").value).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource("0, 30, low", "1, 30, low", "2, 30, medium", "3, 30, high", "5, 0, low")
    fun `ревность нормируется по числу их сообщений`(markers: Int, theirMessages: Int, expected: String) {
        val trait = traits(TestCorpus.coldStats().copy(jealousyMarkers = markers), theirMessages).getValue("jealousy")
        assertThat(trait.value).isEqualTo(expected)
        assertThat(trait.weight).isBetween(0.0, 1.0)
    }

    @Test
    fun `нормировка маркеров и вес в формате numeric(4,3)`() {
        assertThat(TraitExtractor.markerScore(3, 30)).isCloseTo(0.5, within(1e-9))
        assertThat(TraitExtractor.markerScore(30, 30)).isEqualTo(1.0)
        assertThat(TraitExtractor.markerScore(1, 0)).isEqualTo(0.0)
        assertThat(0.12345.toWeight()).isEqualByComparingTo("0.123")
        assertThat(1.7.toWeight()).isEqualByComparingTo("1.000")
        assertThat(Double.NaN.toWeight()).isEqualByComparingTo("0.000")
        assertThat(FULL_WEIGHT.scale()).isEqualTo(3)
    }
}
