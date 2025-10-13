# construo

[![Download](https://img.shields.io/gradle-plugin-portal/v/io.github.fourlastor.construo)](https://plugins.gradle.org/plugin/io.github.fourlastor.construo)

Construo is a gradle plugin to cross compile JVM apps.

## Setup

Add the plugin to your project

Kotlin DSL

```kotlin
plugins {
  id("io.github.fourlastor.construo") version "2.0.1"
}
```

Groovy DSL

```groovy
plugins {
  id "io.github.fourlastor.construo" version "2.0.1"
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
    // Optional, defaults to application.mainClass or jar task main class
    mainClass.set("io.github.fourlastor.gdx.Main")
    // Optional, defaults to $buildDir/construo/dist
    // where to put the packaged zips
    outputDir.set(rootProject.file("dist"))
    // Optional, an alternative jar task name to base the build upon
    jarTask.set("myJarTaskName")
    // Optional, a folder to use as the root in the zip output file
    zipFolder.set("game-v1.0.0")
}
```

### JLink options

You can customize how the minimized image is generated with the `jlink` block.

```kotlin
construo {
    jlink {
        // add arbitrary modules to be included when running jlink
        modules.addAll("jdk.zipfs")
        // guess the modules from the jar using jdeps, defaults to true
        guessModulesFromJar.set(false)
        // include default crypto modules, defaults to true
        includeDefaultCryptoModules.set(false)
    }
}
```

### Roast options

Construo uses [roast](https://github.com/fourlastor-alexandria/roast/) to run the application, a few options can be specified and will be set in its config.json.

```kotlin
construo {
    roast {
        // MacOS only, whether to run the jvm on the main thread, defaults to true
        runOnFirstThread.set(false)
        // use ZGC garbage collector, defaults to true
        useZgc.set(false)
        // use the main class as the context class loader, defaults to false, useful for compose apps
        useMainAsContextClassLoader.set(true)
        // vm startup options
        vmArgs.addAll("-Xmx1G")
    }
}
```

### Defining targets

Targets define the output bundles construo will generate, each target will need to define the architecture, and a JDK url for that specific target (you cannot use a JRE for cross compilation).

#### Windows

Windows targets support only the X86_64 architecture.

The `useGpuHint` option specifies whether the packaged app will use the discrete GPU in hybrid systems (defaults to `true`).

The `useConsole` option specifies whether the packaged app will print output to a terminal (defaults to `false`). Note that `useConsole` overrides `useGpuHint`.

The `icon` option specifies an icon to be used for the executable, this must be a PNG image.

#### Macos

Mac-specific options are mainly used to generate key-value pairs for the Info.plist file. For more information see https://developer.apple.com/documentation/bundleresources/information-property-list

The `identifier` option is mandatory.

Optional but highly recommended are `buildNumber` and `versionNumber`. They are both used for the Info.plist file. buildNumber is for CFBundleVersion, versionNumber is for CFBundleShortVersionString 

An icon can be optionally specified with `macOsIcon` on each target.

Human-readable copyright notice can be added with `copyright` option.

To define app category use `categoryName` option. For possible values see https://developer.apple.com/documentation/bundleresources/information-property-list/lsapplicationcategorytype

If you need any other key-value pairs in your Info.plist, use `additionalInfoFile` option. This should point to an xml file containing key-value pairs, formatted as they are in the Info.plist file.

Optional `entitlementsFile` can be used to pick a file to define entitlements. This file will be copied into the app bundle and renamed to myAppName.entitlements . For possible values see https://developer.apple.com/documentation/bundleresources/entitlements

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
            // Optional but highly recommended; app version number
            buildNumber.set("1.0.0")
            versionNumber.set("1.0")
            // Optional: icon for macOS
            macIcon.set(project.file("path/to/mac-icon.icns"))
            // Optional: set copyright
            copyright.set("© 2025 Fourlastor")
            // Optional: set application category
            categoryName.set("public.app-category.developer-tools")
            // Optional: file to be used as entitlements file.
            entitlementsFile.set(project.file("path/to/mac-entitlements.xml"))
            // Optional: an xml file containing additional key-value pairs to be added to the Info.plist file
            additionalInfoFile.set(project.file("path/to/mac-additional.xml"))
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
            // Optional but highly recommended; app version number
            def projectVersionNumber = project.properties.getOrDefault("version","1.0.0").toString()
            buildNumber.set(projectVersionNumber)
            versionNumber.set(projectVersionNumber)
            // Optional: icon for macOS
            macIcon.set(project.file("path/to/mac-icon.icns"))
            // Optional: set copyright
            copyright.set("© 2025 Fourlastor")
            // Optional: set application category
            categoryName.set("public.app-category.developer-tools")
            // Optional: file to be used as entitlements file.
            entitlementsFile.set(project.file("path/to/mac-entitlements.xml"))
            // Optional: an xml file containing additional key-value pairs to be added to the Info.plist file
            additionalInfoFile.set(project.file("path/to/mac-additional.xml"))
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

### Using ProGuard

You can set a `ProguardTask` as the `jarTask` name, in that case, you will also have to set `mainClass` to the main class name (see [general config](#general-config)).

### Packaging the targets

Each defined target will generate a `packageXXX` task, where `XXX` is the capitalized name of the target (for example: `packageLinuxX64`). Running the task will produce a zip inside the `outputDir` folder containing the fully packaged app.
