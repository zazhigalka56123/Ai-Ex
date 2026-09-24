plugins {
    id("aiex.spring-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":web-common"))
    implementation(lib("spring-boot-starter-webmvc"))
    implementation(lib("spring-boot-starter-data-jpa"))
    implementation(lib("spring-boot-starter-validation"))
    implementation(lib("springdoc-webmvc-api"))

    testImplementation(testFixtures(project(":app")))
}

kover {
    reports {
        verify {
            rule("Покрытие модуля ${project.path} ≥ 60%") {
                minBound(60)
            }
        }
    }
}
