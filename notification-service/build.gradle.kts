plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":reactive-support"))
    implementation(project(":http-clients"))
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation(libs.spring.boot.starter.liquibase)
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:${libs.versions.springdoc.get()}")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation(libs.jackson.module.kotlin)
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    // Liquibase работает через JDBC: приложение ходит в базу по R2DBC, а миграции - отдельным JDBC-соединением при старте.
    runtimeOnly(libs.postgresql)

    testImplementation("io.projectreactor:reactor-test")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
}

tasks.bootJar {
    archiveFileName.set("notification-service.jar")
}
