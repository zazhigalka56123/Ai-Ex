plugins {
    base
    // Подключает build-logic к корневому classpath, иначе app не найдёт плагин Spring Boot.
    id("aiex.spring-conventions") apply false
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":app"))
    kover(project(":iam-service"))
    kover(project(":notification-service"))
    kover(project(":shared-kernel"))
    kover(project(":shared-web"))
    kover(project(":gateway"))
    kover(project(":config-server"))
    kover(project(":discovery-server"))
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
            rule("Общее покрытие проекта ≥ 70%") { minBound(70) }
        }
    }
}

tasks.check { dependsOn(tasks.named("koverVerify")) }
