pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "ai-ex"

include(
    "app",
    "common",
    "web-common",
    "llm",

    "iam:api",
    "iam:impl",

    "persona:api",
    "persona:impl",
    "ingest:impl",

    "agent:api",
    "agent:impl",
    "dialog:api",
    "dialog:impl",

    "care:api",
    "care:impl",
    "admin:impl",
    "notification:impl",
)
