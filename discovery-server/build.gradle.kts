plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(libs.spring.cloud.dependencies))
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
}

tasks.bootJar { archiveFileName.set("service.jar") }
