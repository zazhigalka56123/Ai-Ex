plugins {
    id("aiex.impl-conventions")
}

dependencies {
    implementation(project(":agent:api"))
    implementation(project(":persona:api"))
    implementation(project(":llm"))
}
