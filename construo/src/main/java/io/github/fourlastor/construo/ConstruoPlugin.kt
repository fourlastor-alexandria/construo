package io.github.fourlastor.construo

import io.github.fourlastor.construo.task.DownloadTask
import io.github.fourlastor.construo.task.PackageTask
import io.github.fourlastor.construo.task.jvm.CreateRuntimeImageTask
import io.github.fourlastor.construo.task.jvm.RoastTask
import io.github.fourlastor.construo.task.macos.BuildMacAppBundle
import io.github.fourlastor.construo.task.macos.GeneratePlist
import io.github.fourlastor.construo.task.windows.ReplaceWinIconTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType
import proguard.gradle.ProGuardTask
import java.io.File

class ConstruoPlugin : Plugin<Project> {

    private data class DownloadJdkOptions(
        val url: String,
        val filename: String
    )

    override fun apply(project: Project) {
        val pluginExtension = project.extensions.create("construo", ConstruoPluginExtension::class.java)
        val tasks = project.tasks
        val baseBuildDir = project.layout.buildDirectory.dir("construo")
        val baseRuntimeImageBuildDir = baseBuildDir.map { it.dir("runtime-image") }
        val roastZipDir = baseBuildDir.map { it.dir("roast-zip") }
        val baseRoastExeDir = baseBuildDir.map { it.dir("roast-exe") }
        val jdkDir = baseBuildDir.map { it.dir("jdk") }
        pluginExtension.outputDir.convention(baseBuildDir.map { it.dir("dist") })

        project.plugins.withType(ApplicationPlugin::class.java) {
            val javaApplication = project.extensions.getByType<JavaApplication>()
            pluginExtension.mainClass.set(javaApplication.mainClass)
        }

        // Register the correct tasks for each target
        pluginExtension.targets.all {
            val target = this
            val targetBuildDir = baseBuildDir.map { it.dir(target.name) }
            val targetRuntimeImageBuildDir = baseRuntimeImageBuildDir.map { it.dir("${project.name}-${target.name}") }

            val capitalized = target.name.replaceFirstChar(Char::uppercase)
            val targetArchiveFileName = pluginExtension.name.map { "$it-${target.name}.zip" }

            val jdkUrl = target.jdkUrl.map {
                val extension = if (it.endsWith(".zip")) "zip" else "tar.gz"
                DownloadJdkOptions(
                    url = it,
                    filename = "${target.name}.$extension"
                )
            }

            val jdkDest = jdkUrl.flatMap { url ->
                val extension = if (url.filename.endsWith(".zip")) "zip" else "tar.gz"
                jdkDir.map { it.file("${target.name}.$extension") }
            }

            val downloadJdk = tasks.register("downloadJdk$capitalized", DownloadTask::class.java) {
                group = GROUP_NAME
                src.set(jdkUrl.map { it.url })
                dest.set(jdkDest)
            }
            val targetJdkDir = jdkDir.map { it.dir(target.name) }
            val unzipJdk = tasks.register("unzipJdk$capitalized", Copy::class.java) {
                group = GROUP_NAME
                dependsOn(downloadJdk)
                from(
                    jdkDest.map {
                        if (it.asFile.extension  == "zip") {
                            project.zipTree(it)
                        } else {
                            project.tarTree(it)
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

            val runningJdkRoot = pluginExtension.jdkRoot.orElse(project.layout.dir(project.provider { File(System.getProperty("java.home")) }))
            val jdkTargetRoot = project.layout.dir(unzipJdk.map { it.destinationDir }).findJdkRoot()

            val selectedTask = pluginExtension.jarTask
                .map { tasks.getByName(it) }
                .orElse(project.provider { (tasks.findByName("shadowJar") ?: tasks.findByName("jar")) })

            val mainClass = pluginExtension.mainClass.orElse(selectedTask.map {
                if (it is Jar) {
                    it.manifest.attributes["Main-Class"] as String
                } else {
                    throw IllegalStateException("Setting a custom task which is not a Jar task requires manually setting the mainClass property")
                }
            })

            val jarFileLocation = selectedTask.flatMap {
                when (it) {
                    is Jar -> it.archiveFile
                    is ProGuardTask -> project.layout.file(project.provider { it.outJarFileCollection.first() })
                    else -> throw IllegalStateException("Only supported custom task types are Jar and ProguardTask, it was ${it.javaClass}")
                }
            }

            val createRuntimeImage =
                tasks.register("createRuntimeImage$capitalized", CreateRuntimeImageTask::class.java) {
                    dependsOn(unzipJdk, selectedTask)
                    jdkRoot.set(runningJdkRoot)
                    jarFile.set(jarFileLocation)
                    targetJdkRoot.set(jdkTargetRoot)
                    modules.set(pluginExtension.jlink.modules)
                    includeDefaultCryptoModules.set(pluginExtension.jlink.includeDefaultCryptoModules)
                    guessModulesFromJar.set(pluginExtension.jlink.guessModulesFromJar)
                    multiReleaseVersion.set(pluginExtension.jlink.multiReleaseVersion)
                    output.set(targetRuntimeImageBuildDir)
                }

            fun Target.roastName(): String = when (this) {
                is Target.Windows -> {
                    val architectureSuffix = when (architecture.get()) {
                        Target.Architecture.X86_64 -> "x86_64"
                        Target.Architecture.AARCH64 -> "aarch64"
                    }
                    if (useConsole.getOrElse(false)) {
                        "win-console-$architectureSuffix.exe"
                    } else if (useGpuHint.getOrElse(true)) {
                        "win-$architectureSuffix.exe"
                    } else {
                        "win-no-gpu-$architectureSuffix.exe"
                    }
                }

                is Target.Linux -> when (architecture.get()) {
                    Target.Architecture.X86_64 -> "linux-x86_64"
                    Target.Architecture.AARCH64 -> "linux-aarch64"
                }

                is Target.MacOs -> when (architecture.get()) {
                    Target.Architecture.X86_64 -> "macos-x86_64"
                    Target.Architecture.AARCH64 -> "macos-aarch64"
                }

                else -> error("Unsupported target.")
            }

            val roastVersion = "v1.4.0"
            val downloadRoast = tasks.register("downloadRoast$capitalized", DownloadTask::class.java) {
                group = GROUP_NAME
                src.set("https://github.com/fourlastor-alexandria/roast/releases/download/$roastVersion/roast-${target.roastName()}.zip")
                dest.set(roastZipDir.map { it.file("roast-${target.roastName()}.zip") })
            }
            val targetRoastExeDir = baseRoastExeDir.map { it.dir(target.name) }
            val unzipRoast = tasks.register("unzipRoast$capitalized", Copy::class.java) {
                group = GROUP_NAME
                dependsOn(downloadRoast)
                from(project.zipTree(roastZipDir.map { it.file("roast-${target.roastName()}.zip") }))
                into(targetRoastExeDir)
            }

            val packageDependencies = mutableListOf<TaskProvider<*>>(unzipRoast)
            val targetRoastExeDirWithIcon = targetRoastExeDir.map { it.dir("icon") }
            val originalRoastExe = targetRoastExeDir.map { it.file("roast-${target.roastName()}") }
            val targetRoastExe = if (target is Target.Windows) {
                targetRoastExeDirWithIcon
            } else {
                targetRoastExeDir
            }.map { it.file("roast-${target.roastName()}") }

            if (target is Target.Windows) {
                val replaceIcon = tasks.register("replaceIcon$capitalized", ReplaceWinIconTask::class.java) {
                    dependsOn(unzipRoast)
                    inputFile.set(originalRoastExe)
                    inputIcon.set(target.icon)
                    outputFile.set(targetRoastExe)
                }

                packageDependencies.add(replaceIcon)
            }

            val targetRoastExeName = pluginExtension.name.flatMap { name ->
                targetRoastExe.map {
                    if (it.asFile.extension.isEmpty()) {
                        name
                    } else {
                        "$name.exe"
                    }
                }
            }

            val packageRoast = tasks.register("roast$capitalized", RoastTask::class.java) {
                dependsOn(packageDependencies)
                jdkRoot.set(createRuntimeImage.flatMap { it.output })
                appName.set(pluginExtension.name)
                mainClassName.set(mainClass)
                jarFile.set(jarFileLocation)
                vmArgs.set(pluginExtension.roast.vmArgs)
                useZgc.set(pluginExtension.roast.useZgc)
                runOnFirstThread.set(pluginExtension.roast.runOnFirstThread)
                useMainAsContextClassLoader.set(pluginExtension.roast.useMainAsContextClassLoader)
                output.set(targetRoastDir)
                roastExe.set(targetRoastExe)
                roastExeName.set(targetRoastExeName)
            }

            when (target) {
                is Target.Linux, is Target.Windows -> {
                    tasks.register("package$capitalized", PackageTask::class.java) {
                        group = GROUP_NAME
                        dependsOn(packageRoast)
                        archiveFileName.set(targetArchiveFileName)
                        destinationDirectory.set(pluginExtension.outputDir)
                        from.set(targetRoastDir)
                        into.set(pluginExtension.zipFolder)
                        executable.set(targetRoastDir.flatMap { it.file(targetRoastExeName) })
                    }
                }

                is Target.MacOs -> {
                    val macAppDir = targetBuildDir.flatMap { dir ->
                        pluginExtension.humanName.map { dir.dir("$it.app") }
                    }
                    val pListFile = targetBuildDir.map { it.file("Info.plist") }

                    val generatePlist = tasks.register("generatePList$capitalized", GeneratePlist::class.java) {
                        humanName.set(pluginExtension.humanName)
                        info.set(pluginExtension.info)
                        versionNumber.set(target.versionNumber)
                        buildNumber.set(target.buildNumber)
                        copyright.set(target.copyright)
                        categoryName.set(target.categoryName)
                        executable.set(pluginExtension.name)
                        identifier.set(target.identifier)
                        icon.set(target.macIcon)
                        outputFile.set(pListFile)
                        additional.set(target.additionalInfoFile)
                    }

                    val entitlementsFileName = pluginExtension.humanName.map {"$it.entitlements"}

                    val buildMacAppBundle =
                        tasks.register("buildMacAppBundle$capitalized", BuildMacAppBundle::class.java) {
                            dependsOn(packageRoast, generatePlist)
                            packagedAppDir.set(targetRoastDir)
                            outputDirectory.set(macAppDir)
                            icon.set(target.macIcon)
                            entitlementsFileSource.set(target.entitlementsFile)
                            entitlementsFileDestination.set(entitlementsFileName)
                            plist.set(pListFile)
                        }

                    tasks.register("package$capitalized", PackageTask::class.java) {
                        group = GROUP_NAME
                        archiveFileName.set(targetArchiveFileName)
                        destinationDirectory.set(pluginExtension.outputDir)
                        dependsOn(buildMacAppBundle)
                        from.set(macAppDir)
                        into.set(pluginExtension.humanName.flatMap { humanName ->
                            if (pluginExtension.zipFolder.isPresent) {
                                pluginExtension.zipFolder.map { destination -> "$destination/$humanName.app" }
                            } else {
                                project.provider { "$humanName.app" }
                            }
                        })
                        executable.set(macAppDir.flatMap { it.dir("Contents").dir("MacOS").file(targetRoastExeName) })
                    }
                }
            }
        }
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
    }
}
