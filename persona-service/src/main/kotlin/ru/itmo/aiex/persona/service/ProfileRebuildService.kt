package ru.itmo.aiex.persona.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import ru.itmo.aiex.common.security.Actor
import ru.itmo.aiex.persona.dto.CorpusSnapshot
import ru.itmo.aiex.persona.dto.ProfileRebuildResult
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class ProfileRebuildService(private val transactions: ProfileTransactions, private val drafts: ProfileDraftFactory) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun rebuildFrom(personaId: UUID, snapshot: CorpusSnapshot): ProfileRebuildResult = build(transactions.storeSnapshot(personaId, snapshot))

    fun rebuildManually(actor: Actor, personaId: UUID): ProfileRebuildResult = when (val plan = transactions.planManualRebuild(personaId, actor)) {
        is ManualRebuildPlan.UpToDate -> plan.result.also { log.info("Персона {}: профиль уже собран из последнего корпуса", personaId) }
        is ManualRebuildPlan.Rebuild -> build(plan.target)
    }

    private fun build(target: RebuildTarget): ProfileRebuildResult {
        val startedAt = System.nanoTime()
        val draft = drafts.create(target.personaId, target.personaName, target.snapshot)
        val result = transactions.applyProfile(target, draft)
        log.info(
            "Персона {}: профиль v{} собран из корпуса {} за {} мс",
            target.personaId,
            result.versionNo,
            target.snapshotId,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
        )
        return result
    }
}
