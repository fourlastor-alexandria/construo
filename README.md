# construo

[![Download](https://img.shields.io/gradle-plugin-portal/v/io.github.fourlastor.construo)](https://plugins.gradle.org/plugin/io.github.fourlastor.construo)

Construo is a gradle plugin to cross compile JVM apps.

## Setup

Add the plugin to your project

Kotlin DSL

```kotlin
plugins {
  id("io.github.fourlastor.construo") version "1.1.0"
}
```

Groovy DSL

```groovy
plugins {
  id "io.github.fourlastor.construo" version "1.1.0"
}
```

## Configuration

### General config

These are the base options to set when using construo.

```kotlin
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
}
```

### JLink options

You can add extra modules to the minimized JDK via the `jlink` block.

```kotlin
construo {
    jlink {
        // add extra modules to be included when running jlink
        modules.addAll("jdk.zipfs")
    }
}
```

### Roast options

Construo uses [roast](https://github.com/fourlastor-alexandria/roast/) to run the application, a few options can be specified and will be set in its config.json.

```kotlin
construo {
    roast {
        // use ZGC garbage collector, defaults to true
        useZgc.set(false)
        // use the main class as the context class loader, defaults to false, useful for compose apps
        useMainAsContextClassLoader.set(true)
        // vm startup options
        vmOptions.addAll("-Xmx1G")
    }
}
```

### Defining targets

Targets define the output bundles construo will generate, each target will need to define the architecture, and an optional (if using the [foojay api](#using-foojay-instead-of-jdk-urls)) JDK url for that specific target (you cannot use a JRE for cross compilation).

#### Windows

Windows targets support only the X86_64 architecture.

The `useGpuHint` option specifies whether the packaged app will use the discrete GPU in hybrid systems (defaults to `true`).

#### Macos

The `identifier` option is mandatory.

An icon can be optionally specified with `macOsIcon` on each target.

<details open>
<summary>Kotlin DSL</summary>

```kotlin
import io.github.fourlastor.construo.Target

construo {
    targets {
        create<Target.Linux>("linuxX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz")
        }
        create<Target.MacOs>("macM1") {
            architecture.set(Target.Architecture.AARCH64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.11_9.tar.gz")
            // macOS needs an identifier
            identifier.set("io.github.fourlastor.Game")
            // Optional: icon for macOS
            macIcon.set(project.file("path/to/mac-icon.icns"))
        }
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.11_9.zip")
            // use executable with GPU hints, defaults to true
            useGpuHint.set(false)
        }
    }
}
```
</details>

<details>
<summary>Groovy DSL</summary>

```groovy
import io.github.fourlastor.construo.Target

construo {
    targets.configure {
        create("linuxX64", Target.Linux) {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz")
        }
        create("macM1", Target.MacOs) {
            architecture.set(Target.Architecture.AARCH64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.11_9.tar.gz")
            // macOS needs an identifier
            identifier.set("io.github.fourlastor.Game")
            // Optional: icon for macOS
            macIcon.set(project.file("path/to/mac-icon.icns"))
        }
        create("winX64", Target.Windows) {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.11_9.zip")
            // use executable with GPU hints, defaults to true
            useGpuHint.set(false)
        }
    }
}
```
</details>

Each defined target will generate a `packageXXX` task, where `XXX` is the capitalized name of the target (for example: `packageLinuxX64`). Running the task will produce a zip inside the `outputDir` folder containing the fully packaged app.

### Using foojay instead of JDK URLs

Construo can figure out the JDK url for a specific build via the [foojay discovery api](https://github.com/foojayio/discoapi). This makes the `jdkUrl` option inside `targets` optional.

```kotlin
import io.github.fourlastor.construo.Target

construo {
    toolchain.set(ToolchainOptions(
        ToolchainVersion.of("17.0.9+9.1"),
        JvmVendorSpec.ADOPTIUM,
    ))
    targets {
        create<Target.Linux>("linuxX64") {
            // no need to specify a jdkUrl
            architecture.set(Target.Architecture.X86_64)
        }
    }
}
```

`ToolchainVersion.of()` accepts either a string, which must be a specific JDK version for that vendor, or an integer, which must be a major java version number (ie `ToolchainVersion.of(17)`).
