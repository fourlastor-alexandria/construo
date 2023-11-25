plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
    alias(libs.plugins.nexus.publish)
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

gradlePlugin {
    plugins {
        register("construo") {
            id = "io.github.fourlastor.gdx.construo"
            implementationClass = "io.github.fourlastor.construo.ConstruoPlugin"
        }
    }
}

dependencies {
    implementation(libs.download)
    implementation(libs.xmlBuilder)
    implementation(libs.shadow)
    implementation(libs.kotlinx.serialization)
}

val libVersion: String by project
val publishGroup = "io.github.fourlastor.gdx"

group = publishGroup
version = libVersion

nexusPublishing {
    repositories {
        sonatype()
    }
}


publishing {
    publications {
        afterEvaluate {
            named<MavenPublication>("pluginMaven") {
                artifactId = base.archivesName.get()
                pom {
                    name.set("Construo")
                    description.set("A plugin to cross compile JVM applications")
                    url.set("https://www.github.com/fourlastor-alexandria/construo")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://www.github.com/fourlastor-alexandria/construo/blob/main/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("fourlastor")
                            name.set("Daniele Conti")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://www.github.com/fourlastor-alexandria/construo.git")
                        developerConnection.set("scm:git:https://www.github.com/fourlastor-alexandria/construo.git")
                        url.set("https://www.github.com/fourlastor-alexandria/construo")
                    }
                }
            }
        }
    }
}

signing {
    afterEvaluate {
        setRequired({ project.hasProperty("RELEASE") })
        val signingKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["pluginMaven"])
    }
}
