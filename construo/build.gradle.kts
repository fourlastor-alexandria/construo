plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

spotless {
    isEnforceCheck = false
    kotlin {
        ktlint()
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

base {
    archivesName = "construo"
}

val libVersion: String by project
val publishGroup = "io.github.fourlastor"

group = publishGroup
version = libVersion

gradlePlugin {
    website.set("https://www.github.com/fourlastor-alexandria/construo")
    vcsUrl.set("https://www.github.com/fourlastor-alexandria/construo")
    plugins {
        register("construo") {
            id = "io.github.fourlastor.construo"
            implementationClass = "io.github.fourlastor.construo.ConstruoPlugin"
            displayName = "Construo"
            description = "A plugin to package JVM applications across systems"
            tags = listOf("jlink", "runtime", "package")
        }
    }
}

dependencies {
    implementation(libs.download)
    implementation(libs.square.moshi.core)
    ksp(libs.square.moshi.codegen)
    implementation(libs.square.okhttp)
    implementation(libs.xmlBuilder)
    implementation(libs.shadow)
    implementation(libs.kotlinx.serialization)
}
