plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    application
}

dependencies {
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.websockets)
    implementation(libs.kotlinx.serializationJson)
    implementation(libs.kotlinx.coroutinesCore)
}

application {
    mainClass.set("pl.pelotasplus.eyeofbeholder.agentserver.MainKt")
    applicationName = "eob-agent"
}
