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

include("app")

include("shared-kernel")
include("shared-web")
include("shared-security")
include("config-server")
include("discovery-server")
include("gateway")
include("iam-service")
include("notification-service")
