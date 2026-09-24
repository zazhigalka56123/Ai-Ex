import org.gradle.api.artifacts.ProjectDependency

plugins {
    base
    id("aiex.coverage-aggregation")
}

dependencies {
    subprojects.filter { it.buildFile.exists() }.forEach { kover(project(it.path)) }
}

val checkModuleBoundaries by tasks.registering {
    group = "verification"
    description = "Проверяет, что межмодульные зависимости идут только через api-модули."
    doLast {
        val mainConfigurations = setOf("api", "implementation", "compileOnly", "runtimeOnly")
        val forbiddenInApi = listOf("org.springframework", "jakarta.persistence", "org.hibernate")
        val violations = mutableListOf<String>()
        subprojects.filter { it.buildFile.exists() }.forEach { module ->
            val deps = module.configurations.filter { it.name in mainConfigurations }.flatMap { it.dependencies }
            val projectDeps = deps.filterIsInstance<ProjectDependency>().map { it.path }.toSet()
            val path = module.path
            val allowed: (String) -> Boolean = when {
                path == ":common" || path == ":llm" -> { _ -> false }
                path == ":web-common" -> { dep -> dep == ":common" }
                path.endsWith(":api") -> { dep -> dep == ":common" }
                path == ":agent:impl" -> { dep ->
                    dep.endsWith(":api") || dep in setOf(":common", ":web-common", ":llm")
                }
                path.endsWith(":impl") -> { dep ->
                    dep.endsWith(":api") || dep in setOf(":common", ":web-common")
                }
                else -> { _ -> true }
            }
            projectDeps.filterNot(allowed).forEach { violations += "$path -> $it" }
            if (path.endsWith(":api")) {
                deps.filterNot { it is ProjectDependency || it.name.endsWith("-dependencies") }
                    .filter { dep -> forbiddenInApi.any { dep.group.orEmpty().startsWith(it) } }
                    .forEach { violations += "$path тянет ${it.group}:${it.name} (в api-модуле запрещено)" }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Нарушены границы модулей:\n" + violations.joinToString("\n") { "  - $it" })
        }
        val checked = subprojects.count { it.buildFile.exists() }
        logger.lifecycle("Границы модулей соблюдены: $checked модулей проверено; llm доступен только :agent:impl.")
    }
}

tasks.named("check") { dependsOn(checkModuleBoundaries) }
