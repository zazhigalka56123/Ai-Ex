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

include("app", "contracts", "web-support", "http-clients",
    "account-service", "persona-service", "dialog-service", "care-service", "notification-service",
    "config-server", "discovery-server", "gateway")
