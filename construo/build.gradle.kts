plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.spotless)
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.serialization)
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
val publishGroup = "io.github.fourlastor.gdx"

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
    implementation(libs.xmlBuilder)
    implementation(libs.shadow)
    implementation(libs.kotlinx.serialization)
}
