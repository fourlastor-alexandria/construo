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
            jdkSha256.set("7b175dbe0d6e3c9c23b6ed96449b018308d8fc94a5ecd9c0df8b8bc376c3c18a")
            roastSha256.set("b5567b8a30ec5b6c9fe652e4de9dd455648cc779e3253d04c23df50f04db4ae3")
        }
        create<Target.MacOs>("macX64") {
            architecture.set(Target.Architecture.X86_64)
            identifier.set("io.github.fourlastor.Game")
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_mac_hotspot_17.0.8_7.tar.gz")
            jdkSha256.set("6fea89cea64a0f56ecb9e5d746b4921d2b0a80aa65c92b265ee9db52b44f4d93")
            roastSha256.set("1d96878c26c820876198f87a0ebc523818da04372a2dd9e8b19c900a2772f997")
        }
        create<Target.MacOs>("macM1") {
            architecture.set(Target.Architecture.AARCH64)
            identifier.set("io.github.fourlastor.Game")
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.8_7.tar.gz")
            jdkSha256.set("105d1ada42927fccde215e8c80b43221cd5aad42e6183819c367234ac062fc10")
            roastSha256.set("33ad8745576260d8e006705ee111af35ae7b0c1ff48eaf783f39bbec9d6cc54d")
        }
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip")
            jdkSha256.set("341a7243778802019a100ba7ae32a05a3f4ae5fd64dbf2a970d02f07c7d1c804")
            roastSha256.set("59fc0b82638dc9b67864b0a1157ec8d0b43f856e3c85e94e5eeeb13a9b38fa99")
            if (providers.gradleProperty("useTargetJdkTools").isPresent) {
                packagingToolJdk.set(Target.PackagingToolJdk.TARGET_JDK)
            }
        }
        create<Target.Windows>("winX64NoGpu") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip")
            jdkSha256.set("341a7243778802019a100ba7ae32a05a3f4ae5fd64dbf2a970d02f07c7d1c804")
            roastSha256.set("79ce74f26ebaa468618e752c0a0dd3a534c17f89d3c0859357e7fadd634e9270")
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
