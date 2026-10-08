plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":http-clients"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation(libs.jackson.module.kotlin)
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
}

tasks.bootJar {
    archiveFileName.set("notification-service.jar")
}
