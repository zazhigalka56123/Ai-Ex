plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation(libs.spring.boot.starter.actuator)
}

tasks.bootJar {
    archiveFileName.set("discovery-server.jar")
}
