plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(libs.spring.cloud.dependencies))
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
}

tasks.bootJar { archiveFileName.set("service.jar") }
