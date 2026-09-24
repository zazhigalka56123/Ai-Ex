plugins {
    id("aiex.spring-conventions")
}

dependencies {
    api(project(":common"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.webmvc.api)
    implementation(libs.spring.data.commons)
}

kover {
    reports {
        verify {
            rule("Покрытие модуля web-common ≥ 60%") { minBound(60) }
        }
    }
}
