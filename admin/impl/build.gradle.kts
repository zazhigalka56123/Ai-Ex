plugins {
    id("aiex.impl-conventions")
}

dependencies {
    implementation(project(":persona:api"))
    implementation(project(":dialog:api"))
    implementation(project(":care:api"))
}
