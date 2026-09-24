plugins {
    id("org.jetbrains.kotlinx.kover")
}

kover {
    reports {
        filters {
            excludes {
                classes("*Application", "*ApplicationKt")
                annotatedBy("ru.itmo.aiex.common.ExcludeFromCoverage")
            }
        }
        verify {
            rule("Общее покрытие проекта ≥ 70%") {
                minBound(70)
            }
        }
    }
}
