plugins {
    id("aiex.kotlin-conventions")
    `java-library`
}

dependencies {
    api(libs.spring.data.commons)
    api(libs.jakarta.validation.api)
}
