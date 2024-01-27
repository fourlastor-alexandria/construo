# construo

[![Download](https://img.shields.io/gradle-plugin-portal/v/io.github.fourlastor.construo)](https://plugins.gradle.org/plugin/io.github.fourlastor.construo)

Construo is a gradle plugin to cross compile JVM apps.

## Setup

Add the plugin to your project

Kotlin DSL

```kotlin
plugins {
  id("io.github.fourlastor.construo") version "1.0.1"
}
```

Groovy DSL

```groovy
plugins {
  id "io.github.fourlastor.construo" version "1.0.1"
}
```

## Configuration

Make sure you set a main class in your jar task.

Kotlin DSL:

```kotlin
import io.github.fourlastor.construo.Target

construo {
    // name of the executable
    name.set("game")
    // human-readable name, used for example in the `.app` name for macOS
    humanName.set("Game")
    // Optional, defaults to project version
    version.set("0.0.0")
    // Optional, defaults to application.mainClass or jar task main class
    mainClass.set("io.github.fourlastor.gdx.Main")
    // Optional, defaults to $buildDir/construo/dist
    // where to put the packaged zips
    outputDir.set(rootProject.file("dist"))
    jlink {
        // add extra modules to be included when running jlink
        modules.addAll("jdk.zipfs")
    }
    // these options will be passed to roast via the config.json file
    roast {
        // use zgc garbage collector, defaults to true
        useZgc.set(false)
        // use the main class as the context class loader, defaults to false, useful for compose apps
        useMainAsContextClassLoader.set(true)
        // vm startup options
        vmOptions.addAll("-Xmx1G")
    }
    // outputs configuration
    targets {
        // Linux X64
        create<Target.Linux>("linuxX64") {
            architecture.set(Target.Architecture.X86_64)
            // jdk url to use as a base for the minimized image
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.8_7.tar.gz")
        }
        // macOS ARM processors
        create<Target.MacOs>("macM1") {
            architecture.set(Target.Architecture.AARCH64)
            // macOS needs an identifier
            identifier.set("io.github.fourlastor.Game")
            // Optional: icon for macOS
            macIcon.set(project.file("path/to/mac-icon.icns"))
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.8_7.tar.gz")
        }
        // Windows X64
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip")
            // use executable with GPU hints, defaults to true
            useGpuHint.set(false)
        }
    }
}
```

Groovy DSL:

```groovy
import io.github.fourlastor.construo.Target

construo {
    // name of the executable
    name.set("game")
    // human-readable name, used for example in the `.app` name for macOS
    humanName.set("Game")
    // Optional, defaults to project version
    version.set("0.0.0")
    // Optional, defaults to application.mainClass or jar task main class
    mainClass.set("io.github.fourlastor.gdx.Main")
    // Optional, defaults to $buildDir/construo/dist
    // where to put the packaged zips
    outputDir.set(rootProject.file("dist"))
    jlink {
        // add extra modules to be included when running jlink
        modules.addAll("jdk.zipfs")
    }
    // these options will be passed to roast via the config.json file
    roast {
        // use zgc garbage collector, defaults to true
        useZgc.set(false)
        // use the main class as the context class loader, defaults to false, useful for compose apps
        useMainAsContextClassLoader.set(true)
        // vm startup options
        vmOptions.addAll("-Xmx1G")
    }
    // outputs configuration
    targets.configure {
        // Linux X64
        create("linuxX64", Target.Linux) {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.8_7.tar.gz")
        }
        // macOS ARM processors
        create("macM1", Target.MacOs) {
            architecture.set(Target.Architecture.AARCH64)
            // macOS needs an identifier
            identifier.set("io.github.fourlastor.Game")
            // Optional: icon for macOS
            macIcon.set(project.file("path/to/mac-icon.icns"))
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.8_7.tar.gz")
        }
        // Windows X64
        create("winX64", Target.Windows) {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.zip")
            // use executable with GPU hints, defaults to true
            useGpuHint.set(false)
        }
    }
}
```

Each defined target will generate a `packageXXX` task, where `XXX` is the capitalized name of the target (for example: `packageLinuxX64`). Running the task will produce a zip inside the `outputDir` folder containing the fully packaged app.

> [!NOTE]
> Windows targets support only the X86_64 architecture.
