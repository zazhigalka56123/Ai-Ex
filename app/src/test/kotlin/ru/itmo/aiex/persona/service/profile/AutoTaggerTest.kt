package ru.itmo.aiex.persona.service.profile

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import ru.itmo.aiex.persona.testing.TestCorpus
class AutoTaggerTest {
    private val tagger = AutoTagger()

    @Test
    fun `холодный корпус получает cold, jealous, caps, night-owl, laconic, slow-replier`() {
        val tags = tagger.match(TestCorpus.coldStats(), 30).associate { it.code to it.weight }
        assertThat(tags.keys).containsExactlyInAnyOrder("cold", "jealous", "caps", "night-owl", "laconic", "slow-replier")
        assertThat(tags.getValue("jealous")).isEqualTo(1.0)
        assertThat(tags.getValue("caps")).isEqualTo(0.6)
        assertThat(tags.values).allSatisfy { assertThat(it).isBetween(0.0, 1.0) }
    }

    @Test
    fun `тёплый корпус получает emoji, talkative, fast-replier, affectionate`() {
        val tags = tagger.match(TestCorpus.warmStats(), 30).map { it.code }
        assertThat(tags).containsExactlyInAnyOrder("emoji", "talkative", "fast-replier", "affectionate")
    }

    @Test
    fun `cold - только когда совпали все четыре признака`() {
        val withAffection = TestCorpus.coldStats().copy(affectionMarkers = 5)
        assertThat(tagger.match(withAffection, 30).map { it.code }).doesNotContain("cold")
        val fastReplies = TestCorpus.coldStats().copy(avgReplyDelaySeconds = 60)
        assertThat(tagger.match(fastReplies, 30).map { it.code }).doesNotContain("cold", "slow-replier").contains("fast-replier")
        val unknownSpeed = TestCorpus.coldStats().copy(avgReplyDelaySeconds = null)
        assertThat(tagger.match(unknownSpeed, 30).map { it.code }).doesNotContain("cold", "slow-replier", "fast-replier")
    }

    @Test
    fun `вес вне 0 1 недопустим`() {
        assertThatThrownBy { DerivedTag("x", 1.5) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { DerivedTrait("x", "y", -0.1) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
