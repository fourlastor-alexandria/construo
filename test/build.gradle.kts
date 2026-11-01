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

// A self-signed certificate for the macM1Hardened target: macOS doesn't trust it, but it signs as
// a Developer ID certificate would, hardened runtime included
val testCertificate = layout.buildDirectory.file("test-certificate/construo-test.p12")
val generateTestCertificate by tasks.registering(Exec::class) {
    val keystore = testCertificate.get().asFile
    outputs.file(keystore)
    doFirst {
        keystore.delete()
        keystore.parentFile.mkdirs()
    }
    executable = File(System.getProperty("java.home"), "bin/keytool").path
    args(
        // rcodesign reads only the legacy PKCS#12 encryption
        "-J-Dkeystore.pkcs12.legacy",
        "-genkeypair",
        "-alias", "construo-test",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "3650",
        "-dname", "CN=Construo Test",
        "-ext", "KeyUsage:critical=digitalSignature",
        "-ext", "ExtendedKeyUsage=codeSigning",
        "-storetype", "PKCS12",
        "-keystore", keystore.path,
        "-storepass", "construo",
        "-keypass", "construo",
    )
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
            // ad hoc signature
            signing {
                enabled.set(true)
            }
        }
        create<Target.MacOs>("macM1Hardened") {
            architecture.set(Target.Architecture.AARCH64)
            identifier.set("io.github.fourlastor.Game")
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.8_7.tar.gz")
            prePackageTasks.add(generateTestCertificate.name)
            // signed with a certificate, so with the hardened runtime and the JVM's entitlements
            signing {
                enabled.set(true)
                certificate.set(testCertificate)
                certificatePassword.set("construo")
            }
        }
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip")
            if (providers.gradleProperty("useTargetJdkTools").isPresent) {
                packagingToolJdk.set(Target.PackagingToolJdk.TARGET_JDK)
            }
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
