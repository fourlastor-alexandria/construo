package io.github.fourlastor.construo

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.DownloadTaskPlugin
import org.beryx.runtime.JPackageImageTask
import org.beryx.runtime.RuntimePlugin
import org.beryx.runtime.RuntimeTask
import org.beryx.runtime.data.RuntimePluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.os.OperatingSystem
import java.io.File


class ConstruoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val currentOs: OperatingSystem = OperatingSystem.current()
        val extension = project.extensions.create("construo", ConstructorPluginExtension::class.java, project)

        // Options - move to an extension
        val name = extension.name.get()
        val gameVersion = extension.version.get()
        val packageInputDir = "jpackage/in"
        val packageOutputDir = extension.outputDir.get()
        val linuxIcon = extension.linuxIcon
        val winIcon = extension.winIcon
        val macIcon = extension.macIcon

        val targetDir = File(project.buildDir, "construo")
        val linuxAppDir = "$targetDir/appimage"
        val macAppDir = "$targetDir/mac"
        val imageToolsDir = "$targetDir/appimagetools"
        val jpackageBuildDir = "$targetDir/jpackage"
        val jpackageImageBuildDir = "$jpackageBuildDir/image"


        project.plugins.apply(RuntimePlugin::class.java)
        project.plugins.apply(DownloadTaskPlugin::class.java)
        project.extensions.configure(RuntimePluginExtension::class.java) { extension ->
            extension.options.value(
                listOf(
                    "--strip-debug",
                    "--compress", "2",
                    "--no-header-files",
                    "--no-man-pages"
                )
            )
            if (currentOs.isWindows) {
                extension.options.add("--strip-native-commands")
            }
            extension.modules.value(
                listOf(
                    "java.base",
                    "java.desktop",
                    "java.logging",
                    "jdk.incubator.foreign",
                    "jdk.unsupported",
                )
            )
            extension.launcher {
                val templateUrl = ConstruoPlugin::class.java.getResource("unixrun.mustache")
                    ?: throw GradleException("Unix script template not found")
                it.unixScriptTemplate = project.file(templateUrl.toURI())
            }
            @Suppress("INACCESSIBLE_TYPE")
            if (currentOs.isLinux) {
                extension.targetPlatform("linux-x64") {
                    it.setJdkHome(
                        it.jdkDownload(
                            "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.4.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.4.1_1.tar.gz"
                        )
                    )
                }
                extension.targetPlatform("linux-aarch64") {
                    it.setJdkHome(
                        it.jdkDownload(
                            "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.4.1%2B1/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.4.1_1.tar.gz"
                        )
                    )
                }
                extension.targetPlatform("mac-x64") {
                    it.setJdkHome(
                        it.jdkDownload(
                            "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.4.1%2B1/OpenJDK17U-jdk_x64_mac_hotspot_17.0.4.1_1.tar.gz"
                        )
                    )
                }
                extension.targetPlatform("mac-aarch64") {
                    it.setJdkHome(
                        it.jdkDownload(
                            "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.4.1%2B1/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.4.1_1.tar.gz"
                        )
                    )
                }
            }

            extension.imageDir.dir(jpackageImageBuildDir)

            extension.jpackage {
                it.imageOutputDir = project.file(jpackageBuildDir)
                it.skipInstaller = true
                it.mainJar = "${name}.jar"
                it.imageName = name
                if (currentOs.isWindows && winIcon.isPresent) {
                    it.imageOptions.addAll(
                        listOf(
                            "--icon",
                            winIcon.get().asFile.absolutePath
                        )
                    )
                }
            }
        }

        val tasks = project.tasks

        val architectures = listOf("x64", "aarch64")


        // TODO check folders - one of them should be the linux app image one
        val prepareAppImageFilesTasks = architectures.map { arch ->
            tasks.create("prepareAppImageFiles$arch", Copy::class.java) { task ->
                task.group = GROUP_NAME
                task.dependsOn(tasks.withType(RuntimeTask::class.java))
                task.from(project.file("${targetDir}/desktop-linux-$arch")) {
                    it.into(APP_DIR_NAME)
                }
                task.from(project.file("${targetDir}/Game.AppDir.Template")) {
                    it.into(APP_DIR_NAME)
                }
                task.from(project.file(linuxIcon)) {
                    it.into(APP_DIR_NAME)
                }
                task.into(project.file("$linuxAppDir/$arch"))
            }
        }

        val prepareAppImageFiles = tasks.register("prepareAppImageFiles") { task ->
            task.group = GROUP_NAME
            prepareAppImageFilesTasks.forEach {
                task.dependsOn(it)
            }
        }

        val downloadAppImageTools = tasks.create("downloadAppImageTools", Download::class.java) {
            it.group = GROUP_NAME
            it.src(
                listOf(
                    "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage",
                    "https://github.com/AppImage/AppImageKit/releases/download/continuous/runtime-x86_64",
                    "https://github.com/AppImage/AppImageKit/releases/download/continuous/runtime-aarch64",
                )
            )
            it.dest(imageToolsDir)
            it.overwrite(false)
        }

        val prepareAppImageTools = tasks.create("prepareAppImageTools", Exec::class.java) {
            it.group = GROUP_NAME
            it.dependsOn(downloadAppImageTools)
            it.workingDir(imageToolsDir)
            it.commandLine("chmod", "+x", "appimagetool-x86_64.AppImage")
        }

        val buildAppImagesTasks = architectures.map { arch ->
            tasks.create("buildAppImage$arch", Exec::class.java) { task ->
                task.dependsOn(prepareAppImageFiles, prepareAppImageTools)
                task.workingDir(imageToolsDir)
                task.environment(mapOf("ARCH" to appImageArch(arch)))
                task.commandLine(
                    "./appimagetool-x86_64.AppImage", "-n", "$linuxAppDir/$arch/$APP_DIR_NAME",
                    "$linuxAppDir/$APP_IMAGE_NAME-$arch",
                    "--runtime-file", "./${runtimeName(arch)}"
                )
            }
        }

        val buildAppImage = tasks.create("buildAppImage") { task ->
            task.group = GROUP_NAME
            buildAppImagesTasks.forEach {
                task.dependsOn(it)
            }
        }

        val packageLinuxTasks = architectures.map { arch ->
            tasks.create("packageLinux$arch", Zip::class.java) { task ->
                task.group = GROUP_NAME
                task.archiveFileName.set("$name-linux-$arch.zip")
                task.destinationDirectory.set(project.file(packageOutputDir))
                task.dependsOn(buildAppImage)
                task.from(project.file("$linuxAppDir/$APP_IMAGE_NAME-$arch"))
                task.into("$name-v$gameVersion-linux-$arch")
            }
        }

        tasks.create("packageLinux") { task ->
            task.group = GROUP_NAME
            packageLinuxTasks.forEach {
                task.dependsOn(it)
            }
        }

        val buildMacAppTasks = architectures.map { arch ->
            tasks.create("buildMacAppBundle$arch", Copy::class.java) { task ->
                task.group = GROUP_NAME
                task.from(project.file("$jpackageImageBuildDir/desktop-mac-$arch")) {
                    it.into("MacOS")
                }
                task.from(project.file(macIcon)) {
                    it.into("Resources")
                }
                task.from(project.file("$packageInputDir/mac-app-folder/Info.plist"))
                task.into(project.file("$macAppDir/$arch/$name.app/Contents"))
            }
        }

        val buildMacAppBundle = tasks.create("buildMacAppBundle") { task ->
            task.group = GROUP_NAME
            buildMacAppTasks.forEach {
                task.dependsOn(it)
            }
        }

        val packageMacTasks = architectures.map { arch ->
            tasks.create("packageMac$arch", Zip::class.java) { task ->
                task.group = GROUP_NAME
                task.archiveFileName.set("$name-mac-$arch.zip")
                task.destinationDirectory.set(project.file(packageOutputDir))
                task.dependsOn(buildMacAppBundle)
                task.from(project.file("$macAppDir/$arch"))
                task.into("$name-v$gameVersion-mac-$arch")
            }
        }

        tasks.create("packageMac") { task ->
            task.group = GROUP_NAME
            packageMacTasks.forEach {
                task.dependsOn(it)
            }
        }

        tasks.create("packageWindows", Zip::class.java) { task ->
            task.group = GROUP_NAME
            task.archiveFileName.set("$name-windows-x64.zip")
            task.destinationDirectory.set(project.file(packageOutputDir))
            task.dependsOn(tasks.withType(JPackageImageTask::class.java))
            task.from(project.file(jpackageBuildDir))
            task.into("$name-v$gameVersion-windows-x64")
        }
    }

    companion object {
        private const val GROUP_NAME = "package"
        private const val APP_DIR_NAME = "Game.AppDir"
        private const val APP_IMAGE_NAME = "Game"

        private fun runtimeName(arch: String): String = when (arch) {
            "aarch64" -> "runtime-aarch64"
            "x64" -> "runtime-x86_64"
            else -> error("Runtime for architecture $arch unknown")
        }

        private fun appImageArch(arch: String) = when (arch) {
            "aarch64" -> "arm_aarch64"
            "x64" -> "x86_64"
            else -> error("AppImage arch for architecture $arch unknown")
        }
    }
}
