plugins {
    id("aiex.spring-conventions")
    `java-library`
}

dependencies {
    api(project(":shared-kernel"))
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    api("io.projectreactor:reactor-core")
    compileOnly("org.springframework:spring-webflux")
    implementation(platform(libs.spring.cloud.dependencies))
    api("io.github.openfeign:feign-core")
}
