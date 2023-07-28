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

tasks.test {
    useJUnitPlatform()
}

application {
    applicationName = "game"
    mainClass.set("io.github.fourlastor.gdx.Main")
}

tasks.withType<Jar> {
    manifest {
        attributes.put("Main-Class", "io.github.fourlastor.gdx.Main")
    }
}

construo {
    name.set("game")
    humanName.set("Game")
    version.set("0.0.0")
    linuxIcon.set(rootProject.file("icon.png"))
    outputDir.set(rootProject.file("dist"))
    targets {
        create<Target.Linux>("linuxX64") {
            architecture.set(Architecture.X64)
        }
        create<Target.Linux>("linuxAarch64") {
            architecture.set(Architecture.AARCH64)
        }
        create<Target.MacOs>("macX64") {
            architecture.set(Architecture.X64)
            identifier.set("io.github.fourlastor.Game")
        }
        create<Target.Windows>("winX64") {
            architecture.set(Architecture.X64)
        }
    }
}
