plugins {
    id("aiex.spring-conventions")
    `java-library`
}

dependencies {
    api(project(":contracts"))
    api("org.springframework.boot:spring-boot-starter-webflux")
    api(libs.spring.boot.starter.validation)
    implementation("org.springframework:spring-tx")
    implementation(libs.jackson.module.kotlin)

    testImplementation("io.projectreactor:reactor-test")
}
