plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":web-support"))
    implementation(project(":http-clients"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.liquibase)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.json)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.jackson.module.kotlin)
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("io.projectreactor:reactor-core")
    runtimeOnly(libs.postgresql)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation("io.projectreactor:reactor-test")
}

tasks.jar { enabled = true }
tasks.bootJar { archiveFileName.set("account-service.jar") }
