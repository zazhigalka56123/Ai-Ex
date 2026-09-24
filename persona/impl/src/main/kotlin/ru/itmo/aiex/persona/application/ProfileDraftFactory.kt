package ru.itmo.aiex.persona.application

import org.springframework.stereotype.Component
import ru.itmo.aiex.agent.api.DescribePersonaCommand
import ru.itmo.aiex.agent.api.PersonaDescriber
import ru.itmo.aiex.agent.api.PersonaTraitLine
import ru.itmo.aiex.persona.api.CorpusSnapshot
import ru.itmo.aiex.persona.api.StyleView
import ru.itmo.aiex.persona.domain.profile.AutoTagger
import ru.itmo.aiex.persona.domain.profile.DerivedTrait
import ru.itmo.aiex.persona.domain.profile.PromptBuilder
import ru.itmo.aiex.persona.domain.profile.StyleFactory
import ru.itmo.aiex.persona.domain.profile.TraitExtractor
import java.util.UUID

@Component
class ProfileDraftFactory(private val describer: PersonaDescriber, private val json: PersonaJson) {
    private val traitExtractor = TraitExtractor()
    private val autoTagger = AutoTagger()

    fun create(personaId: UUID, personaName: String, snapshot: CorpusSnapshot): ProfileDraft {
        val traits = traitExtractor.extract(snapshot.stats, snapshot.theirMessages)
        val style = StyleFactory.from(snapshot)
        return ProfileDraft(
            traits = traits,
            autoTags = autoTagger.match(snapshot.stats, snapshot.theirMessages),
            style = style,
            summary = summarize(personaId, personaName, style, traits),
            styleJson = json.write(style),
            statsJson = json.write(snapshot.stats),
        )
    }

    private fun summarize(personaId: UUID, personaName: String, style: StyleView, traits: List<DerivedTrait>): String = describer
        .describe(
            DescribePersonaCommand(
                personaId = personaId,
                personaName = personaName,
                traits = traits.map { PersonaTraitLine(PromptBuilder.traitLabel(it.key), it.value, it.weight) },
                samplePhrases = style.samplePhrases,
            ),
        ).text
}
