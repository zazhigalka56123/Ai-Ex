plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    testImplementation("org.springframework.security:spring-security-test")
    implementation(platform(libs.spring.cloud.dependencies))
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("io.projectreactor:reactor-core")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(project(":shared-web"))
    runtimeOnly(libs.postgresql)
    testImplementation("io.projectreactor:reactor-test")
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.bootJar { archiveFileName.set("service.jar") }
