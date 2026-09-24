plugins {
    id("aiex.kotlin-conventions")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.jetbrains.kotlin.plugin.jpa")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

dependencies {
    implementation(lib("kotlin-reflect"))
    testImplementation(lib("spring-boot-starter-test"))
    testImplementation(lib("springmockk"))
}
