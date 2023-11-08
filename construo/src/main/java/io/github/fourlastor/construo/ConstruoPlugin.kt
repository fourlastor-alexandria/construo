package io.github.fourlastor.construo

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.DownloadTaskPlugin
import io.github.fourlastor.construo.task.jvm.CreateRuntimeImageTask
import io.github.fourlastor.construo.task.jvm.RoastTask
import io.github.fourlastor.construo.task.linux.BuildAppImage
import io.github.fourlastor.construo.task.linux.GenerateAppRun
import io.github.fourlastor.construo.task.linux.GenerateDesktopEntry
import io.github.fourlastor.construo.task.linux.PrepareAppImageFiles
import io.github.fourlastor.construo.task.linux.PrepareAppImageTools
import io.github.fourlastor.construo.task.macos.BuildMacAppBundle
import io.github.fourlastor.construo.task.macos.GeneratePlist
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import java.io.File

class ConstruoPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply(DownloadTaskPlugin::class.java)
        val pluginExtension = project.extensions.create("construo", ConstruoPluginExtension::class.java)
        val tasks = project.tasks
        val baseBuildDir = project.layout.buildDirectory.dir("construo")
        val baseRuntimeImageBuildDir = baseBuildDir.map { it.dir("runtime-image") }
        val imageToolsDir = baseBuildDir.map { it.dir("appimagetools") }
        val roastZipDir = baseBuildDir.map { it.dir("roast-zip") }
        val baseRoastExeDir = baseBuildDir.map { it.dir("roast-exe") }
        val jdkDir = baseBuildDir.map { it.dir("jdk") }

        // Generic tasks, these are lazy because they need to be instantiated only if a specific platform is used.
        val downloadAppImageTools by lazy {
            tasks.register("downloadAppImageTools", Download::class.java) {
                group = GROUP_NAME
                src(
                    listOf(
                        "https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage",
                        "https://github.com/AppImage/AppImageKit/releases/download/continuous/runtime-x86_64",
                        "https://github.com/AppImage/AppImageKit/releases/download/continuous/runtime-aarch64"
                    )
                )
                dest(imageToolsDir)
                overwrite(false)
            }
        }
        val prepareAppImageTools by lazy {
            val downloadTask = downloadAppImageTools.get()
            tasks.register("prepareAppImageTools", PrepareAppImageTools::class.java) {
                imagesToolsDir.set(imageToolsDir)
                dependsOn(downloadTask)
            }
        }
        val buildAppImages by lazy {
            tasks.register("buildAppImages") {
                group = GROUP_NAME
            }
        }
        val packageLinuxMain by lazy {
            tasks.register("packageLinux") {
                group = GROUP_NAME
            }
        }
        val buildMacAppBundles by lazy {
            tasks.register("buildMacAppBundle") {
                group = GROUP_NAME
            }
        }
        val packageMacMain by lazy {
            tasks.register("packageMac") {
                group = GROUP_NAME
            }
        }

        // Register the correct tasks for each target
        pluginExtension.targets.all(
            Action {
                val target = this
                val targetBuildDir = baseBuildDir.map { it.dir(target.name) }
                val targetRuntimeImageBuildDir = baseRuntimeImageBuildDir.map { it.dir("${project.name}-${target.name}") }

                val capitalized = target.name.capitalized()
                val targetArchiveFileName = pluginExtension.name.map { "$it-${target.name}.zip" }
                val packageDestination = pluginExtension.name.flatMap { name ->
                    pluginExtension.version.map { version -> "$name-$version-${target.name}" }
                }

                val downloadJdk = tasks.register("downloadJdk$capitalized", Download::class.java) {
                    group = GROUP_NAME
                    src(listOf(target.jdkUrl))
                    dest(
                        target.jdkUrl.flatMap { url ->
                            val extension = if (url.endsWith(".zip")) "zip" else "tar.gz"
                            jdkDir.map { it.file("${target.name}.$extension") }
                        }
                    )
                    overwrite(false)
                }
                val targetJdkDir = jdkDir.map { it.dir(target.name) }
                val unzipJdk = tasks.register("unzipJdk$capitalized", Copy::class.java) {
                    group = GROUP_NAME
                    dependsOn(downloadJdk)
                    from(
                        downloadJdk.map {
                            if (it.dest.extension == "zip") {
                                project.zipTree(it.dest)
                            } else {
                                project.tarTree(it.dest)
                            }
                        }
                    ) {
                        exclude("**/legal/**")
                    }
                    into(targetJdkDir)
                    doFirst {
                        targetJdkDir.get().asFile.deleteRecursively()
                    }
                }
                val targetRoastDir = targetBuildDir.map { it.dir("roast") }

                val runningJdkRoot = File(System.getProperty("java.home"))
                val jdkTargetRoot = project.layout.dir(unzipJdk.map { it.destinationDir }).findJdkRoot()
                // TODO: check for shadow jar as well
                val jarTask = tasks.named("jar", Jar::class.java)
                val jarFileLocation = jarTask.flatMap { it.archiveFile }

                val createRuntimeImage =
                    tasks.register("createRuntimeImage$capitalized", CreateRuntimeImageTask::class.java) {
                        dependsOn(unzipJdk, jarTask)
                        jdkRoot.set(runningJdkRoot)
                        jarFile.set(jarFileLocation)
                        targetJdkRoot.set(jdkTargetRoot)
                        output.set(targetRuntimeImageBuildDir)
                    }

                fun Target.roastName(): String = when (this) {
                    is Target.Windows -> {
                        check(architecture.get() == Architecture.X86_64) { "Only Windows 64 bit is supported" }
                        "win-64.exe"
                    }
                    is Target.Linux -> when (architecture.get()) {
                        Architecture.X86_64 -> "linux-x86_64"
                        Architecture.AARCH64 -> "linux-aarch64"
                    }
                    is Target.MacOs -> when (architecture.get()) {
                        Architecture.X86_64 -> "macos-x86_64"
                        Architecture.AARCH64 -> "macos-aarch64"
                    }
                    else -> error("Unsupported target.")
                }

                val downloadRoast = tasks.register("downloadRoast$capitalized", Download::class.java) {
                    group = GROUP_NAME
                    src("https://github.com/fourlastor-alexandria/roast/releases/download/v0.0.2/roast-${target.roastName()}.zip")
                    dest(roastZipDir)
                    overwrite(false)
                }
                val targetRoastExeDir = baseRoastExeDir.map { it.dir(target.name) }
                val unzipRoast = tasks.register("unzipRoast$capitalized", Copy::class.java) {
                    dependsOn(downloadRoast)
                    from(project.zipTree(roastZipDir.map { it.file("roast-${target.roastName()}.zip") }))
                    into(targetRoastExeDir)
                }

                val packageRoast = tasks.register("roast$capitalized", RoastTask::class.java) {
                    dependsOn(unzipRoast)
                    targetProperty.set(target)
                    jdkRoot.set(createRuntimeImage.flatMap { it.output })
                    appName.set(pluginExtension.name)
                    mainClassName.set(pluginExtension.mainClassName)
                    jarFile.set(jarFileLocation)
                    output.set(targetRoastDir)
                    roastExe.set(targetRoastExeDir.map { it.file("roast-${target.roastName()}") })
                }

                when (target) {
                    is Target.Linux -> {
                        val linuxAppDir = targetBuildDir.map { it.dir(APP_DIR_NAME) }
                        val targetTemplateAppDir = targetBuildDir.map { it.dir("Game.AppDir.Template") }
                        val linuxAppImage = targetBuildDir.flatMap { dir ->
                            target.architecture.map { architecture ->
                                dir.file("$APP_IMAGE_NAME-${architecture.arch}")
                            }
                        }
                        val generateAppRun =
                            tasks.register("generateAppRun$capitalized", GenerateAppRun::class.java) {
                                executable.set(pluginExtension.name)
                                outputFile.set(targetTemplateAppDir.map { it.file("AppRun") })
                            }

                        val generateDesktopEntry =
                            tasks.create("generateDesktopEntry$capitalized", GenerateDesktopEntry::class.java) {
                                icon.set(pluginExtension.linuxIcon)
                                humanName.set(pluginExtension.humanName)
                                executable.set(pluginExtension.name)
                                version.set(pluginExtension.version)
                                architecture.set(target.architecture)
                                outputFile.set(targetTemplateAppDir.map { it.file("game.desktop") })
                            }

                        val prepareAppImageFiles = tasks.register(
                            "prepareAppImageFiles$capitalized",
                            PrepareAppImageFiles::class.java
                        ) {
                            dependsOn(
                                createRuntimeImage,
                                generateAppRun,
                                generateDesktopEntry
                            )
                            templateAppDir.set(targetTemplateAppDir)
                            // TODO: this should get the files from the roast dir instead
                            jpackageImageBuildDir.set(targetRuntimeImageBuildDir)
                            outputDir.set(linuxAppDir)
                            icon.set(pluginExtension.linuxIcon)
                        }

                        val prepareAppImageToolsTask = prepareAppImageTools.get()
                        val buildAppImage = tasks.register("buildAppImage$capitalized", BuildAppImage::class.java) {
                            dependsOn(
                                prepareAppImageToolsTask,
                                prepareAppImageFiles
                            )
                            imagesToolsDir.set(imageToolsDir)
                            imageDir.set(linuxAppDir)
                            appImageFile.set(linuxAppImage)
                            architecture.set(target.architecture)
                        }

                        buildAppImages.get().dependsOn(buildAppImage)

                        val packageLinux = tasks.register("package$capitalized", Zip::class.java) {
                            group = GROUP_NAME
                            dependsOn(buildAppImage)
                            archiveFileName.set(targetArchiveFileName)
                            destinationDirectory.set(pluginExtension.outputDir)
                            from(linuxAppImage)
                            into(packageDestination)
                        }

                        packageLinuxMain.get().dependsOn(packageLinux)
                    }

                    is Target.MacOs -> {
                        val macAppDir = targetBuildDir.flatMap { dir ->
                            pluginExtension.name.map { dir.dir("$it.app") }
                        }
                        val pListFile = targetBuildDir.map { it.file("Info.plist") }
                        val generatePlist = tasks.register("generatePList$capitalized", GeneratePlist::class.java) {
                            humanName.set(pluginExtension.humanName)
                            info.set(pluginExtension.info)
                            executable.set(pluginExtension.name)
                            identifier.set(pluginExtension.identifier)
                            icon.set(pluginExtension.macIcon)
                            outputFile.set(pListFile)
                        }

                        val buildMacAppBundle =
                            tasks.register("buildMacAppBundle$capitalized", BuildMacAppBundle::class.java) {
                                dependsOn(packageRoast, generatePlist)
                                packagedAppDir.set(targetRoastDir)
                                outputDirectory.set(macAppDir)
                                icon.set(pluginExtension.macIcon)
                                plist.set(pListFile)
                            }

                        buildMacAppBundles.get().dependsOn(buildMacAppBundle)

                        val packageMac = tasks.register("package$capitalized", Zip::class.java) {
                            group = GROUP_NAME
                            archiveFileName.set(targetArchiveFileName)
                            destinationDirectory.set(pluginExtension.outputDir)
                            dependsOn(buildMacAppBundle)
                            from(macAppDir)
                            into(packageDestination)
                        }

                        packageMacMain.get().dependsOn(packageMac)
                    }

                    is Target.Windows -> {
                        tasks.register("package$capitalized", Zip::class.java) {
                            group = GROUP_NAME
                            archiveFileName.set(targetArchiveFileName)
                            destinationDirectory.set(pluginExtension.outputDir)
                            dependsOn(packageRoast)
                            from(targetRoastDir)
                            into(packageDestination)
                        }
                    }
                }
            }
        )
    }

    private fun Provider<Directory>.findJdkRoot() = this.map { root ->
        val dir = root.asFile
            .walkTopDown()
            .firstOrNull { File(it, "bin/java").isFile || File(it, "bin/java.exe").isFile }
            ?.path ?: throw RuntimeException("Couldn't find java home in ${root.asFile.absolutePath}")
        root.dir(dir)
    }

    companion object {
        const val GROUP_NAME = "construo"
        const val APP_DIR_NAME = "Game.AppDir"
        const val APP_IMAGE_NAME = "Game"
    }
}
