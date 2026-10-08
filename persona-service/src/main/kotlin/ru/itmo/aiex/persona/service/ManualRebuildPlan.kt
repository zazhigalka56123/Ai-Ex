package ru.itmo.aiex.persona.service

import ru.itmo.aiex.persona.dto.ProfileRebuildResult
sealed interface ManualRebuildPlan {
    data class UpToDate(val result: ProfileRebuildResult) : ManualRebuildPlan

    data class Rebuild(val target: RebuildTarget) : ManualRebuildPlan
}
