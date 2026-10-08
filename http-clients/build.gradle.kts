plugins {
    id("aiex.spring-conventions")
    `java-library`
}

dependencies {
    api(project(":contracts"))
    api("org.springframework.cloud:spring-cloud-starter-openfeign")
    implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
    implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
    implementation(libs.spring.boot.starter.json)
    implementation(libs.jackson.module.kotlin)
}
