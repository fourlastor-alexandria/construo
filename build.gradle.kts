@Suppress(
    // known false positive: https://youtrack.jetbrains.com/issue/KTIJ-19369
    "DSL_SCOPE_VIOLATION"
)
plugins {
    alias(libs.plugins.nexus.publish)
}

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

val libVersion: String by project
val publishGroup = "io.github.fourlastor.gdx"

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
    version = libVersion
    group = publishGroup
}

group = publishGroup
version = libVersion

nexusPublishing {
    repositories {
        sonatype()
    }
}
