plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    id("org.graalvm.buildtools.native") version "0.10.6"
}

group = "com.vhtor"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "com.vhtor.MainKt"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(libs.logback.classic)
    implementation("io.ktor:ktor-server-content-negotiation:3.5.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("rinha-backend-2026")
            mainClass.set("com.vhtor.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-H:+ReportExceptionStackTraces")
            buildArgs.add("--initialize-at-build-time=kotlin")
        }
    }
}
