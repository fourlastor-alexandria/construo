import io.github.fourlastor.construo.Target

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    java
    application
    alias(libs.plugins.spotless)
    id("io.github.fourlastor.gdx.construo")
}

group = "io.github.fourlastor.gdx"
version = "1.0.0"


spotless {
    isEnforceCheck = false
    java {
        palantirJavaFormat()
    }
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

application {
    applicationName = "game"
    mainClass.set("io.github.fourlastor.gdx.Main")
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.github.fourlastor.gdx.Main"
    }
}

construo {
    name.set("game")
    humanName.set("Game")
    version.set("0.0.0")
    outputDir.set(rootProject.file("dist"))
    targets {
        create<Target.Linux>("linuxX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.8_7.tar.gz")
        }
        create<Target.MacOs>("macX64") {
            architecture.set(Target.Architecture.X86_64)
            identifier.set("io.github.fourlastor.Game")
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_mac_hotspot_17.0.8_7.tar.gz")
        }
        create<Target.MacOs>("macM1") {
            architecture.set(Target.Architecture.AARCH64)
            identifier.set("io.github.fourlastor.Game")
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.8_7.tar.gz")
        }
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip")
        }
    }
}

dependencies {
    implementation(libs.gdx.core)
    nativesDesktop(libs.gdx.platform)
    implementation(libs.gdx.backend.lwjgl3)
}

fun DependencyHandlerScope.nativesDesktop(
    provider: Provider<MinimalExternalModuleDependency>,
) = runtimeOnly(variantOf(provider) { classifier("natives-desktop") })
