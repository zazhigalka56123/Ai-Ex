plugins {
    id("aiex.spring-conventions")
    `java-library`
}

dependencies {
    api(project(":contracts"))
    implementation(project(":http-clients"))
    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.starter.data.jpa)
    api(libs.spring.boot.starter.validation)
    api(libs.springdoc.webmvc.ui)
    api(libs.jackson.module.kotlin)
}
