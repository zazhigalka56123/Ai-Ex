plugins {
    id("aiex.impl-conventions")
}

dependencies {
    implementation(project(":persona:api"))
    implementation(project(":agent:api"))
    implementation(libs.jackson.module.kotlin)

    testImplementation(project(":llm"))
}
