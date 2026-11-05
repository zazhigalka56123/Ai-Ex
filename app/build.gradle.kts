plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
    `java-test-fixtures`
}

dependencies {
    implementation(project(":shared-web"))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")

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

    testFixturesApi(project(":shared-web"))
    testFixturesImplementation(platform(libs.spring.boot.dependencies))
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
    archiveFileName.set("service.jar")
}

kover {
    reports {
        verify {
            rule("Общее покрытие проекта ≥ 70%") { minBound(70) }
        }
    }
}
