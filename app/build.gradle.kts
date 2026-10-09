plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
    `java-test-fixtures`
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":web-support"))
    // account-service в микросервисной сборке работает на WebFlux; монолит лаб. 1 остаётся на Spring MVC.
    implementation(project(":account-service")) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-webflux")
        exclude(group = "org.springdoc", module = "springdoc-openapi-starter-webflux-ui")
    }
    implementation(project(":persona-service"))
    implementation(project(":dialog-service"))
    implementation(project(":care-service"))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.liquibase)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.json)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.jackson.module.kotlin)
    runtimeOnly(libs.postgresql)

    testFixturesImplementation(platform(libs.spring.boot.dependencies))
    testFixturesApi(project(":contracts"))
    testFixturesApi(project(":web-support"))
    testFixturesApi(platform(libs.spring.boot.dependencies))
    testFixturesApi(libs.spring.boot.starter.test)
    testFixturesApi(libs.spring.boot.starter.webmvc.test)
    testFixturesApi(libs.spring.boot.testcontainers)
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers.junit.jupiter)
    testFixturesApi(libs.springmockk)
    testFixturesApi(libs.awaitility.kotlin)
    testFixturesApi(libs.jackson.module.kotlin)
    testFixturesApi(libs.spring.boot.starter.data.jpa)

    testImplementation(libs.archunit.junit5)
}

tasks.test {
    systemProperty("aiex.rootDir", rootDir.absolutePath)
    systemProperty("openapi.snapshot.update", System.getProperty("openapi.snapshot.update") ?: "false")
    inputs.files(rootProject.file("docs/openapi/operations.snapshot.json")).withPropertyName("openApiSnapshot")
}

tasks.bootJar {
    archiveFileName.set("ai-ex.jar")
}

kover {
    reports {
        verify {
            rule("Общее покрытие проекта ≥ 70%") { minBound(70) }
        }
    }
}
