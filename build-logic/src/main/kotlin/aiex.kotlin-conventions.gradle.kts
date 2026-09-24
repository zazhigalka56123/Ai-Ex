import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.getSupportedKotlinVersion
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

// Проекты :iam:api и :agent:api называются одинаково, поэтому группа включает родителя -
// иначе у них совпадут координаты и Gradle схлопнет их в один модуль.
group = listOfNotNull("ru.itmo.aiex", project.path.trim(':').substringBeforeLast(':', "").takeIf { it.isNotEmpty() }).joinToString(".")
version = "0.1.0"

// ...и по той же причине jar называется iam-api.jar, а не api.jar: иначе все они
// схлопнутся в одну запись BOOT-INF/lib при сборке bootJar.
base {
    archivesName.set(project.path.trim(':').replace(':', '-'))
}

kotlin {
    jvmToolchain(catalogVersion("java").toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(platform(lib("spring-boot-dependencies")))
    testImplementation(platform(lib("spring-boot-dependencies")))
    testImplementation(lib("junit-jupiter"))
    testImplementation(lib("assertj-core"))
    testImplementation(lib("mockk"))
    testRuntimeOnly(lib("junit-platform-launcher"))
}

ktlint {
    version.set(catalogVersion("ktlint"))
    filter {
        exclude { it.file.path.contains("${File.separator}build${File.separator}") }
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(getSupportedKotlinVersion())
        }
    }
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        xml.required.set(false)
        sarif.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    testLogging {
        events("failed", "skipped")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

kover {
    reports {
        filters {
            excludes {
                classes("*Application", "*ApplicationKt")
                annotatedBy("ru.itmo.aiex.common.ExcludeFromCoverage")
            }
        }
    }
}
