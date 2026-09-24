package ru.itmo.aiex.persona.application

import ru.itmo.aiex.persona.api.ProfileRebuildResult

sealed interface ManualRebuildPlan {
    data class UpToDate(val result: ProfileRebuildResult) : ManualRebuildPlan

    data class Rebuild(val target: RebuildTarget) : ManualRebuildPlan
}
