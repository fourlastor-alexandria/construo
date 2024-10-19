import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.shadow)
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
        target("src")
    }
}

java {
    withJavadocJar()
    withSourcesJar()
}

base {
    archivesName = "construo"
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.apache", "construo.apache")
}

val libVersion: String by project
val publishGroup = "io.github.fourlastor"

group = publishGroup
version = libVersion

kotlin {
    compilerOptions {
        languageVersion.set(KotlinVersion.KOTLIN_1_7)
        apiVersion.set(KotlinVersion.KOTLIN_1_7)
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

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
    implementation(libs.apache.commons.compress)
    shadow(libs.square.okhttp)
    shadow(libs.xmlBuilder)
    shadow(libs.shadow)
    shadow(libs.kotlinx.serialization)
    shadow(libs.guardsquare.proguard)
}
