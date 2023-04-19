package io.github.fourlastor.construo

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.DownloadTaskPlugin
import io.github.fourlastor.construo.task.linux.BuildAppImage
import io.github.fourlastor.construo.task.linux.GenerateAppRun
import io.github.fourlastor.construo.task.linux.GenerateDesktopEntry
import io.github.fourlastor.construo.task.linux.PrepareAppImageFiles
import io.github.fourlastor.construo.task.linux.PrepareAppImageTools
import io.github.fourlastor.construo.task.macos.BuildMacAppBundle
import io.github.fourlastor.construo.task.macos.GeneratePlist
import org.beryx.runtime.RuntimePlugin
import org.beryx.runtime.data.RuntimePluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.os.OperatingSystem

class ConstruoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val currentOs: OperatingSystem = OperatingSystem.current()
        project.plugins.apply(RuntimePlugin::class.java)
        project.plugins.apply(DownloadTaskPlugin::class.java)
        val pluginExtension = project.extensions.create("construo", ConstruoPluginExtension::class.java)
        val tasks = project.tasks
        val baseBuildDir = project.layout.buildDirectory.dir("construo")
        val jpackageBuildDir = baseBuildDir.map { it.dir("jpackage") }
        val baseJpackageImageBuildDir = jpackageBuildDir.map { it.dir("image") }
        val imageToolsDir = baseBuildDir.map { it.dir("appimagetools") }

        // Generic tasks, these are lazy because they need to be instantiated only if a specific platform is used.
        val downloadAppImageTools by lazy {
            tasks.register("downloadAppImageTools", Download::class.java) {
                it.group = GROUP_NAME
                it.src(
                    listOf(
                        "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage",
                        "https://github.com/AppImage/AppImageKit/releases/download/continuous/runtime-x86_64",
                        "https://github.com/AppImage/AppImageKit/releases/download/continuous/runtime-aarch64"
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
        val buildMacAppBundles by lazy {
            tasks.register("buildMacAppBundle") {
                it.group = GROUP_NAME
            }
        }
        val packageMacMain by lazy {
            tasks.register("packageMac") {
                it.group = GROUP_NAME
            }
        }

        // Register the correct tasks for each target
        pluginExtension.targets.all { target ->
            val targetBuildDir = baseBuildDir.map { it.dir(target.name) }
            val jpackageImageBuildDir = baseJpackageImageBuildDir.map { it.dir(target.name) }

            val capitalized = target.name.capitalized()
            val archiveFileName = pluginExtension.name.map { "$it-${target.name}.zip" }
            val packageDestination = pluginExtension.name.flatMap { name ->
                pluginExtension.version.flatMap { version ->
                    pluginExtension.outputDir.map { it.dir("$name-$version-${target.name}") }
                }
            }

            project.extensions.configure(RuntimePluginExtension::class.java) { extension ->
                extension.targetPlatform(target.name) {
                    @Suppress("INACCESSIBLE_TYPE")
                    it.setJdkHome(
                        it.jdkDownload(
                            "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.4.1%2B1/OpenJDK17U-jdk_x64_linux_hotspot_17.0.4.1_1.tar.gz"
                        )
                    )
                }
            }

            when (target) {
                is Target.Linux -> {
                    val linuxAppDir = targetBuildDir.map { it.dir(APP_DIR_NAME) }
                    val templateAppDir = targetBuildDir.map { it.dir("Game.AppDir.Template") }
                    val linuxAppImage = targetBuildDir.flatMap { dir ->
                        target.architecture.map { architecture ->
                            dir.file("$APP_IMAGE_NAME-${architecture.arch}")
                        }
                    }
                    val generateAppRun =
                        tasks.register("generateAppRun$capitalized", GenerateAppRun::class.java) { task ->
                            task.executable.set(pluginExtension.name)
                            task.outputFile.set(templateAppDir.map { it.file("AppRun") })
                        }

                    val generateDesktopEntry =
                        tasks.create("generateDesktopEntry$capitalized", GenerateDesktopEntry::class.java) { task ->
                            task.icon.set(pluginExtension.linuxIcon)
                            task.humanName.set(pluginExtension.humanName)
                            task.executable.set(pluginExtension.name)
                            task.version.set(pluginExtension.version)
                            task.architecture.set(target.architecture)
                            task.outputFile.set(templateAppDir.map { it.file("game.desktop") })
                        }

                    val prepareAppImageFiles = tasks.register(
                        "prepareAppImageFiles$capitalized",
                        PrepareAppImageFiles::class.java
                    ) { task ->
                        task.dependsOn(
                            tasks.named(RuntimePlugin.getTASK_NAME_RUNTIME()),
                            generateAppRun,
                            generateDesktopEntry
                        )
                        task.templateAppDir.set(templateAppDir)
                        task.jpackageImageBuildDir.set(jpackageImageBuildDir)
                        task.outputDir.set(linuxAppDir)
                        task.icon.set(pluginExtension.linuxIcon)
                    }

                    val prepareAppImageToolsTask = prepareAppImageTools.get()
                    val buildAppImage = tasks.register("buildAppImage$capitalized", BuildAppImage::class.java) {
                        it.dependsOn(
                            prepareAppImageToolsTask,
                            prepareAppImageFiles
                        )
                        it.imagesToolsDir.set(imageToolsDir)
                        it.imageDir.set(linuxAppDir)
                        it.appImageFile.set(linuxAppImage)
                        it.architecture.set(target.architecture)
                    }

                    buildAppImages.get().dependsOn(buildAppImage)

                    val packageLinux = tasks.register("package$capitalized", Zip::class.java) { task ->
                        task.group = GROUP_NAME
                        task.dependsOn(buildAppImage)
                        task.archiveFileName.set(archiveFileName)
                        task.destinationDirectory.set(pluginExtension.outputDir)
                        task.from(linuxAppImage)
                        task.into(packageDestination)
                    }

                    packageLinuxMain.get().dependsOn(packageLinux)
                }

                is Target.MacOs -> {
                    val macAppDir = targetBuildDir.flatMap { dir ->
                        pluginExtension.name.map { dir.dir("$it.app") }
                    }
                    val pListFile = targetBuildDir.map { it.file("Info.plist") }
                    val generatePlist = tasks.register("generatePList$capitalized", GeneratePlist::class.java) { task ->
                        task.humanName.set(pluginExtension.humanName)
                        task.info.set(pluginExtension.info)
                        task.executable.set(pluginExtension.name)
                        task.identifier.set(pluginExtension.identifier)
                        task.icon.set(pluginExtension.macIcon)
                        task.outputFile.set(pListFile)
                    }

                    val buildMacAppBundle =
                        tasks.register("buildMacAppBundle$capitalized", BuildMacAppBundle::class.java) { task ->
                            task.dependsOn(
                                tasks.named(RuntimePlugin.getTASK_NAME_RUNTIME()),
                                generatePlist
                            )
                            task.jpackageImageBuildDir.set(jpackageImageBuildDir)
                            task.outputDirectory.set(macAppDir)
                            task.icon.set(pluginExtension.macIcon)
                            task.plist.set(pListFile)
                        }

                    buildMacAppBundles.get().dependsOn(buildMacAppBundle)

                    val packageMac = tasks.register("package$capitalized", Zip::class.java) { task ->
                        task.group = GROUP_NAME
                        task.archiveFileName.set(archiveFileName)
                        task.destinationDirectory.set(pluginExtension.outputDir)
                        task.dependsOn(buildMacAppBundle)
                        task.from(macAppDir)
                        task.into(packageDestination)
                    }

                    packageMacMain.get().dependsOn(packageMac)
                }

                is Target.Windows -> {
                    tasks.register("packageWindows", Zip::class.java) { task ->
                        task.group = GROUP_NAME
                        task.archiveFileName.set(archiveFileName)
                        task.destinationDirectory.set(pluginExtension.outputDir)
                        task.dependsOn(tasks.named(RuntimePlugin.getTASK_NAME_JPACKAGE_IMAGE()))
                        task.from(jpackageBuildDir)
                        task.into(packageDestination)
                    }
                }
            }
        }

        project.extensions.configure(RuntimePluginExtension::class.java) { extension ->
            extension.options.value(
                listOf(
                    "--strip-debug",
                    "--compress",
                    "2",
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
                    "jdk.unsupported"
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

            extension.imageDir.set(baseJpackageImageBuildDir)
            extension.jpackageData.set(
                jpackageBuildDir.flatMap { jpackageBuildDir ->
                    pluginExtension.name.flatMap { name ->
                        extension.jpackageData.map {
                            it.apply {
                                imageOutputDir = jpackageBuildDir.asFile
                                skipInstaller = true
                                imageName = name
                                // TODO win icon
                                // if (currentOs.isWindows && pluginExtension.winIcon.isPresent) {
                                //     it.imageOptions.addAll(
                                //         listOf(
                                //             "--icon",
                                //             pluginExtension.winIcon.get().asFile.absolutePath
                                //         )
                                //     )
                                // }
                            }
                        }
                    }
                }
            )
        }
    }

    companion object {
        const val GROUP_NAME = "construo"
        const val APP_DIR_NAME = "Game.AppDir"
        const val APP_IMAGE_NAME = "Game"
    }
}
