import io.github.fourlastor.construo.Architecture
import io.github.fourlastor.construo.Target

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress(
    // known false positive: https://youtrack.jetbrains.com/issue/KTIJ-19369
    "DSL_SCOPE_VIOLATION"
)
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

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.github.fourlastor.gdx.Main")
}

construo {
    name.set("hi")
    humanName.set("Hello World")
    version.set("0.0.0")
    targets {
        create<Target.Linux>("linuxX64") {
            architecture.set(Architecture.X64)
        }
        create<Target.Linux>("linuxAarch64") {
            architecture.set(Architecture.AARCH64)
        }
        create<Target.MacOs>("macX64") {
            architecture.set(Architecture.X64)
        }
        create<Target.Windows>("winX64") {
            architecture.set(Architecture.X64)
        }
    }
}
