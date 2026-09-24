plugins {
    id("aiex.spring-conventions")
}

dependencies {
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.json)
    implementation(libs.jackson.module.kotlin)
}

kover {
    reports {
        verify {
            rule("Покрытие модуля llm ≥ 60%") { minBound(60) }
        }
    }
}
