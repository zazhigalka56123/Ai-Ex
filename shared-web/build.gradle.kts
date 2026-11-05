plugins {
    id("aiex.spring-conventions")
    `java-library`
}
dependencies {
    api(project(":shared-kernel"))
    api(project(":shared-security"))
    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.starter.validation)
    api(libs.springdoc.webmvc.ui)
    api(libs.hibernate.core)
    api(libs.spring.data.commons)
    api(libs.jackson.module.kotlin)
    implementation("org.springframework:spring-tx")
}
