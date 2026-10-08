plugins {
    id("aiex.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation("org.springframework.cloud:spring-cloud-config-server")
    implementation(libs.spring.boot.starter.actuator)
}

tasks.processResources {
    from(rootProject.file("config-repository")) {
        into("config-repository")
    }
}

tasks.bootJar {
    archiveFileName.set("config-server.jar")
}
