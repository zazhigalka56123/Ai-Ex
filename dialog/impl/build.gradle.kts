plugins {
    id("aiex.impl-conventions")
}

dependencies {
    implementation(project(":dialog:api"))
    implementation(project(":persona:api"))
    implementation(project(":agent:api"))
    implementation(project(":care:api"))
    implementation(libs.spring.boot.starter.websocket)
}
