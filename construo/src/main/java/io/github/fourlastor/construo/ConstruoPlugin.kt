package io.github.fourlastor.construo

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.DownloadTaskPlugin
import io.github.fourlastor.construo.task.linux.BuildAppImage
import io.github.fourlastor.construo.task.linux.PrepareAppImageFiles
import io.github.fourlastor.construo.task.linux.PrepareAppImageTools
import org.beryx.runtime.JPackageImageTask
import org.beryx.runtime.RuntimePlugin
import org.beryx.runtime.data.RuntimePluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.os.OperatingSystem


class ConstruoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val currentOs: OperatingSystem = OperatingSystem.current()
        project.plugins.apply(RuntimePlugin::class.java)
        project.plugins.apply(DownloadTaskPlugin::class.java)
        val pluginExtension = project.extensions.create("construo", ConstruoPluginExtension::class.java)
        val architectures = listOf("x64", "aarch64")
        val tasks = project.tasks
        val targetDir = project.layout.buildDirectory.dir("construo")
        val jpackageBuildDir = targetDir.map { it.dir("jpackage") }
        val jpackageImageBuildDir = jpackageBuildDir.map { it.dir("image") }
        val linuxAppDir = targetDir.map { it.dir("linux") }
        val macAppDir = targetDir.map { it.dir("mac") }
        val imageToolsDir = targetDir.map { it.dir("appimagetools") }
        val templateAppDir = targetDir.map { it.dir("Game.AppDir.Template") }


        val downloadAppImageTools by lazy {
            tasks.register("downloadAppImageTools", Download::class.java) {
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
        }

        val prepareAppImageTools by lazy {
            val downloadTask = downloadAppImageTools.get()
            tasks.register("prepareAppImageTools", PrepareAppImageTools::class.java) {
                it.imagesToolsDir.set(imageToolsDir)
                it.dependsOn(downloadTask)
            }
        }

        val buildAppImages by lazy {
            tasks.register("buildAppImages") { task ->
                task.group = GROUP_NAME
            }
        }

        val packageLinuxMain by lazy {
            tasks.register("packageLinux") { task ->
                task.group = GROUP_NAME
            }
        }

        pluginExtension.targets.all { target ->
            tasks.register("debugTarget${target.name.capitalized()}") {
                it.group = GROUP_NAME
                it.doLast {
                    println("Target ${target.name}: ${target}")
                }
            }
            when (target) {
                is Target.Linux -> {
                    val capitalized = target.name.capitalized()
                    val prepareAppImageFiles = tasks.register(
                        "prepareAppImageFiles$capitalized",
                        PrepareAppImageFiles::class.java
                    ) { task ->
                        task.dependsOn(tasks.named(RuntimePlugin.getTASK_NAME_RUNTIME()))
                        task.targetName.set(pluginExtension.name)
                        task.architecture.set(target.architecture)
                        task.templateAppDir.set(templateAppDir)
                        task.jpackageImageBuildDir.set(jpackageImageBuildDir)
                        task.outputDir.set(linuxAppDir)
                    }

                    val prepareAppImageToolsTask = prepareAppImageTools.get()
                    val buildAppImage = tasks.register("buildAppImage$capitalized", BuildAppImage::class.java) {
                        it.dependsOn(prepareAppImageToolsTask)
                        it.dependsOn(prepareAppImageFiles)
                        it.imagesToolsDir.set(imageToolsDir)
                        it.inputDir.set(linuxAppDir)
                        it.outputDir.set(linuxAppDir)
                        it.architecture.set(target.architecture)
                    }

                    buildAppImages.get().dependsOn(buildAppImage)

                    val packageLinux = tasks.register("package$capitalized", Zip::class.java) { task ->
                        task.group = GROUP_NAME
                        val archiveFileName = pluginExtension.name.flatMap { name ->
                            target.architecture.map { architecture ->
                                "$name-${target.name}-${architecture.arch}"
                            }
                        }
                        task.archiveFileName.set(archiveFileName)
                        task.destinationDirectory.set(pluginExtension.outputDir)
                        task.dependsOn(buildAppImage)
                        val fromDir = linuxAppDir.flatMap { dir ->
                            target.architecture.map { architecture ->
                                dir.dir("$APP_IMAGE_NAME-${architecture.arch}")
                            }
                        }
                        task.from(fromDir)
                        val folderDir = pluginExtension.name.flatMap { name ->
                            pluginExtension.version.map { version ->
                                "${name}-v${version}-${target.name}"
                            }
                        }
                        task.into(folderDir)
                    }

                    packageLinuxMain.get().dependsOn(packageLinux)
                }
            }
        }

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
                val templateUrl = ConstruoPlugin::class.java.getResource("/unixrun.mustache")
                    ?: throw GradleException("Unix script template not found")
                it.unixScriptTemplate = project.resources.text.fromUri(templateUrl.toURI()).asFile()
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

            extension.imageDir.set(jpackageImageBuildDir)

            extension.jpackage {
                it.imageOutputDir = project.file(jpackageBuildDir)
                it.skipInstaller = true
//                it.mainJar = "${pluginExtension.name.get()}.jar"
//                it.imageName = pluginExtension.name.get()
//                if (currentOs.isWindows && pluginExtension.winIcon.isPresent) {
//                    it.imageOptions.addAll(
//                        listOf(
//                            "--icon",
//                            pluginExtension.winIcon.get().asFile.absolutePath
//                        )
//                    )
//                }
            }
        }

        return

        val buildMacAppTasks = architectures.map { arch ->
            tasks.register("buildMacAppBundle$arch", Copy::class.java) { task ->
                task.group = GROUP_NAME
                task.from(project.file("$jpackageImageBuildDir/${pluginExtension.name.get()}-mac-$arch")) {
                    it.into("MacOS")
                }
                // todo check if present
                task.from(project.file(pluginExtension.macIcon.get())) {
                    it.into("Resources")
                }
                // TODO add plist file
                // task.from(project.file("plistFile.xml"))
                task.into(project.file("$macAppDir/$arch/${pluginExtension.name.get()}.app/Contents"))
            }
        }

        val buildMacAppBundle = tasks.register("buildMacAppBundle") { task ->
            task.group = GROUP_NAME
            buildMacAppTasks.forEach {
                task.dependsOn(it)
            }
        }

        val packageMacTasks = architectures.map { arch ->
            tasks.register("packageMac$arch", Zip::class.java) { task ->
                task.group = GROUP_NAME
                task.archiveFileName.set("${pluginExtension.name.get()}-mac-$arch.zip")
                task.destinationDirectory.set(project.file(pluginExtension.outputDir.get()))
                task.dependsOn(buildMacAppBundle)
                task.from(project.file("$macAppDir/$arch"))
                task.into("${pluginExtension.name.get()}-v${pluginExtension.version.get()}-mac-$arch")
            }
        }

        tasks.register("packageMac") { task ->
            task.group = GROUP_NAME
            packageMacTasks.forEach {
                task.dependsOn(it)
            }
        }

        tasks.register("packageWindows", Zip::class.java) { task ->
            task.group = GROUP_NAME
            task.archiveFileName.set("${pluginExtension.name.get()}-windows-x64.zip")
            task.destinationDirectory.set(project.file(pluginExtension.outputDir.get()))
            task.dependsOn(tasks.withType(JPackageImageTask::class.java))
            task.from(project.file(jpackageBuildDir))
            task.into("${pluginExtension.name.get()}-v${pluginExtension.version.get()}-windows-x64")
        }
    }

    companion object {
        const val GROUP_NAME = "construo"
        const val APP_DIR_NAME = "Game.AppDir"
        const val APP_IMAGE_NAME = "Game"

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
