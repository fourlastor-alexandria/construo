import io.github.fourlastor.construo.Target
import io.github.fourlastor.construo.ToolchainOptions
import io.github.fourlastor.construo.ToolchainVersion

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
    id("io.github.fourlastor.construo")
}

group = "io.github.fourlastor"
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

construo {
    name.set("game")
    humanName.set("Game")
    targets {
        create<Target.Linux>("linuxX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz")
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
        create<Target.Windows>("winX64NoGpu") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip")
            useGpuHint.set(false)
        }
    }
    jlink {
        modules.addAll("jdk.zipfs")
    }
    roast {
        useZgc.set(false)
        useMainAsContextClassLoader.set(true)
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
