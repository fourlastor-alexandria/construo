# construo

[![Download](https://img.shields.io/gradle-plugin-portal/v/io.github.fourlastor.construo)](https://plugins.gradle.org/plugin/io.github.fourlastor.construo)

Construo is a gradle plugin to cross compile JVM apps.

## Setup

Add the plugin to your project, replacing `<version>` with the latest version shown in the badge above

Kotlin DSL

```kotlin
plugins {
  id("io.github.fourlastor.construo") version "<version>"
}
```

Groovy DSL

```groovy
plugins {
  id "io.github.fourlastor.construo" version "<version>"
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
    // Optional tasks which must run before every package task
    prePackageTasks.addAll("generateAssets")
    // Optional local files to include, mapped to their package-relative path
    packageFiles.set(mapOf(
        "runtime/common.txt" to layout.projectDirectory.file("assets/common.txt"),
    ))
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
        // Roast release coordinates, defaults to v1.6.0 on GitHub Releases
        version.set("v1.6.0")
        baseUrl.set("https://github.com/fourlastor-alexandria/roast/releases/download")
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

### rcodesign options

macOS apps are signed and notarized with [rcodesign](https://github.com/indygreg/apple-platform-rs), which construo downloads for the build host when a target signs its app (see [signing and notarizing](#signing-and-notarizing)).

```kotlin
construo {
    rcodesign {
        // rcodesign release coordinates, defaults to 0.29.0 on GitHub Releases
        version.set("0.29.0")
        baseUrl.set("https://github.com/indygreg/apple-platform-rs/releases/download")
        // SHA-256 of the archive for the build host, known for the default version
        sha256.set("<rcodesign-sha256>")
        // Optional, an rcodesign to use instead of downloading one
        executable.set(file("/usr/local/bin/rcodesign"))
    }
}
```

### Defining targets

Targets define the output bundles Construo will generate. Each target must define its architecture and a JDK URL (a JRE is not sufficient). Optional SHA-256 digests can pin the JDK and Roast downloads; when configured, each digest is verified before its archive is extracted.

By default, packaging tools such as `jlink` and `jdeps` come from the host JDK for compatibility. Set `packagingToolJdk` to `Target.PackagingToolJdk.TARGET_JDK` to run the tools from the downloaded target JDK when its operating system and architecture are executable on the build host. `archiveFile` can override the complete output ZIP path for a target. A target-specific `roastUrl` can override the URL derived from the global Roast coordinates.

#### Windows

Windows targets support only the X86_64 architecture.

The `useGpuHint` option specifies whether the packaged app will use the discrete GPU in hybrid systems (defaults to `true`).

The `useConsole` option specifies whether the packaged app will print output to a terminal (defaults to `false`). Note that `useConsole` overrides `useGpuHint`.

The `icon` option specifies an icon to be used for the executable, this must be a PNG image.

#### Macos

Mac-specific options are mainly used to generate key-value pairs for the Info.plist file. For more information see https://developer.apple.com/documentation/bundleresources/information-property-list

The `identifier` option is mandatory when building an app bundle.

Set `appBundle` to `false` to package a raw macOS command-line executable and its runtime at the root of the archive instead of generating a `.app` bundle. It defaults to `true`.

Optional but highly recommended are `buildNumber` and `versionNumber`. They are both used for the Info.plist file. buildNumber is for CFBundleVersion, versionNumber is for CFBundleShortVersionString 

An icon can be optionally specified with `macOsIcon` on each target.

Human-readable copyright notice can be added with `copyright` option.

To define app category use `categoryName` option. For possible values see https://developer.apple.com/documentation/bundleresources/information-property-list/lsapplicationcategorytype

If you need any other key-value pairs in your Info.plist, use `additionalInfoFile` option. This should point to an xml file containing key-value pairs, formatted as they are in the Info.plist file.

Optional `entitlementsFile` can be used to pick a file to define entitlements. This file will be copied into the app bundle and renamed to myAppName.entitlements, unless construo signs the app, in which case the entitlements are part of its signature. For possible values see https://developer.apple.com/documentation/bundleresources/entitlements

#### Signing and notarizing

Construo can sign the app bundle, and have Apple notarize it, from Linux or macOS hosts (not Windows). Both are off by default.

- `signing.enabled` signs the app. Without a certificate, the signature is ad hoc (self-signed): the app opens when it's copied to a Mac, but when it's downloaded macOS holds it back until it's allowed in System Settings > Privacy & Security (Open Anyway), or with `xattr -dr com.apple.quarantine Game.app`.
- `signing.certificate` signs it with a certificate instead: a `.p12` file with the certificate and its private key, opened with `signing.certificatePassword`. rcodesign reads only the legacy encryption of `.p12` files: if it reports an incorrect password, export the certificate again with `openssl pkcs12 -export -legacy`.
- `signing.hardenedRuntime` turns on the hardened runtime, which notarization requires. It defaults to `true` with a certificate. The app then gets the entitlements the JVM needs under it (`com.apple.security.cs.allow-jit`, `com.apple.security.cs.allow-unsigned-executable-memory` and `com.apple.security.cs.disable-library-validation`), unless `entitlementsFile` is set: then that file is used, and it must have them too.
- `notarization.enabled` has Apple notarize the signed app, and staples the ticket to it, so it opens with no warning. It turns signing on, and needs a "Developer ID Application" certificate and an [App Store Connect API key](https://appstoreconnect.apple.com/access/integrations/api): its issuer ID, its key ID and its private key file (`AuthKey_<key ID>.p8`).

When signing with a certificate, the native libraries inside jars (LWJGL's, for example) are signed too, as Apple's notary service checks them. A signed app keeps only its launcher in `Contents/MacOS`, where code signing expects code: the runtime, the config and the jar are in `Contents/Resources`, linked from `Contents/MacOS`. The same signing settings can be shared by all macOS targets with `targets.withType<Target.MacOs>().configureEach { signing { ... } }`.

<details open>
<summary>Kotlin DSL</summary>

```kotlin
import io.github.fourlastor.construo.Target

construo {
    targets {
        create<Target.Linux>("linuxX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.11_9.tar.gz")
            jdkSha256.set("<jdk-sha256>")
            roastSha256.set("<roast-sha256>")
        }
        create<Target.MacOs>("macM1") {
            architecture.set(Target.Architecture.AARCH64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.11_9.tar.gz")
            jdkSha256.set("<jdk-sha256>")
            roastSha256.set("<roast-sha256>")
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
            // Optional: sign the app, ad hoc without a certificate
            signing {
                enabled.set(true)
                certificate.set(project.file("path/to/developer-id.p12"))
                certificatePassword.set(providers.environmentVariable("MAC_CERTIFICATE_PASSWORD"))
            }
            // Optional: have Apple notarize the signed app, with an App Store Connect API key
            notarization {
                enabled.set(true)
                issuerId.set(providers.environmentVariable("NOTARY_ISSUER_ID"))
                keyId.set(providers.environmentVariable("NOTARY_KEY_ID"))
                privateKeyFile.set(project.file("path/to/AuthKey_KEYID.p8"))
            }
        }
        create<Target.Windows>("winX64") {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.11_9.zip")
            jdkSha256.set("<jdk-sha256>")
            roastSha256.set("<roast-sha256>")
            // use executable with GPU hints, defaults to true
            useGpuHint.set(false)
            // Optional tasks and files used only by this target
            prePackageTasks.add("generateWindowsAssets")
            packageFiles.set(mapOf(
                "runtime/windows.dll" to layout.projectDirectory.file("assets/windows.dll"),
            ))
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
            jdkSha256.set("<jdk-sha256>")
            roastSha256.set("<roast-sha256>")
        }
        create("macM1", Target.MacOs) {
            architecture.set(Target.Architecture.AARCH64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.11_9.tar.gz")
            jdkSha256.set("<jdk-sha256>")
            roastSha256.set("<roast-sha256>")
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
            // Optional: sign the app, ad hoc without a certificate
            signing {
                enabled.set(true)
                certificate.set(project.file("path/to/developer-id.p12"))
                certificatePassword.set(providers.environmentVariable("MAC_CERTIFICATE_PASSWORD"))
            }
            // Optional: have Apple notarize the signed app, with an App Store Connect API key
            notarization {
                enabled.set(true)
                issuerId.set(providers.environmentVariable("NOTARY_ISSUER_ID"))
                keyId.set(providers.environmentVariable("NOTARY_KEY_ID"))
                privateKeyFile.set(project.file("path/to/AuthKey_KEYID.p8"))
            }
        }
        create("winX64", Target.Windows) {
            architecture.set(Target.Architecture.X86_64)
            jdkUrl.set("https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.11_9.zip")
            jdkSha256.set("<jdk-sha256>")
            roastSha256.set("<roast-sha256>")
            // use executable with GPU hints, defaults to true
            useGpuHint.set(false)
            // Optional tasks and files used only by this target
            prePackageTasks.add("generateWindowsAssets")
            packageFiles.set([
                "runtime/windows.dll": layout.projectDirectory.file("assets/windows.dll"),
            ])
        }
    }
}
```
</details>

### Using ProGuard

You can set a `ProguardTask` as the `jarTask` name, in that case, you will also have to set `mainClass` to the main class name (see [general config](#general-config)).

### Packaging the targets

Each defined target will generate a `packageXXX` task, where `XXX` is the capitalized name of the target (for example: `packageLinuxX64`). Running the task will produce a ZIP inside the `outputDir` folder containing the fully packaged app, unless that target sets an explicit `archiveFile`. A macOS target that signs or notarizes its app does it before packaging, with its `signMacAppBundleXXX` and `notarizeMacAppBundleXXX` tasks.
