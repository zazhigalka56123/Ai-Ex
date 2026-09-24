plugins {
    id("aiex.impl-conventions")
}

dependencies {
    implementation(project(":care:api"))
    implementation(project(":iam:api"))
    implementation(project(":dialog:api"))
}
