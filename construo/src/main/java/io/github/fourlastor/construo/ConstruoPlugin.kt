package io.github.fourlastor.construo

import de.undercouch.gradle.tasks.download.Download
import de.undercouch.gradle.tasks.download.DownloadTaskPlugin
import io.github.fourlastor.construo.foojay.FooJayClient
import io.github.fourlastor.construo.task.PackageTask
import io.github.fourlastor.construo.task.jvm.CreateRuntimeImageTask
import io.github.fourlastor.construo.task.jvm.RoastTask
import io.github.fourlastor.construo.task.macos.BuildMacAppBundle
import io.github.fourlastor.construo.task.macos.GeneratePlist
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType
import proguard.gradle.ProGuardTask
import java.io.File

class ConstruoPlugin : Plugin<Project> {

    private val fooJayClient = FooJayClient()

    private data class DownloadJdkOptions(
        val url: String,
        val filename: String
    )

    override fun apply(project: Project) {
        project.plugins.apply(DownloadTaskPlugin::class.java)
        val pluginExtension = project.extensions.create("construo", ConstruoPluginExtension::class.java)
        val tasks = project.tasks
        val baseBuildDir = project.layout.buildDirectory.dir("construo")
        val baseRuntimeImageBuildDir = baseBuildDir.map { it.dir("runtime-image") }
        val roastZipDir = baseBuildDir.map { it.dir("roast-zip") }
        val baseRoastExeDir = baseBuildDir.map { it.dir("roast-exe") }
        val jdkDir = baseBuildDir.map { it.dir("jdk") }
        project.gradle.projectsEvaluated {
            pluginExtension.version.convention(project.version.toString())
        }
        pluginExtension.outputDir.convention(baseBuildDir.map { it.dir("dist") })
        pluginExtension.roast.useZgc.convention(true)
        pluginExtension.roast.useMainAsContextClassLoader.convention(false)

        project.plugins.withType(ApplicationPlugin::class.java) {
            val javaApplication = project.extensions.getByType<JavaApplication>()
            pluginExtension.mainClass.set(javaApplication.mainClass)
        }

        // Register the correct tasks for each target
        pluginExtension.targets.all {
            val target = this
            val targetBuildDir = baseBuildDir.map { it.dir(target.name) }
            val targetRuntimeImageBuildDir = baseRuntimeImageBuildDir.map { it.dir("${project.name}-${target.name}") }

            val capitalized = target.name.capitalized()
            val targetArchiveFileName = pluginExtension.name.map { "$it-${target.name}.zip" }
            val packageDestination = pluginExtension.name.flatMap { name ->
                pluginExtension.version.map { version -> "$name-$version-${target.name}" }
            }

            val url = target.jdkUrl.map {
                val extension = if (it.endsWith(".zip")) "zip" else "tar.gz"
                DownloadJdkOptions(
                    url = it,
                    filename = "${target.name}.$extension"
                )
            }.orElse(
                pluginExtension.toolchain.map {
                    val packageInfo = fooJayClient.getPackageInfo(it, target)
                    DownloadJdkOptions(
                        url = packageInfo.directDownloadUri,
                        filename = packageInfo.filename
                    )
                }
            )

            val downloadJdk = tasks.register("downloadJdk$capitalized", Download::class.java) {
                group = GROUP_NAME
                src(listOf(url.map { it.url }))
                dest(
                    url.flatMap { url ->
                        val extension = if (url.filename.endsWith(".zip")) "zip" else "tar.gz"
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
                    guessModulesFromJar.set(pluginExtension.jlink.guessModulesFromJar)
                    output.set(targetRuntimeImageBuildDir)
                }

            fun Target.roastName(): String = when (this) {
                is Target.Windows -> {
                    check(architecture.get() == Target.Architecture.X86_64) { "Only Windows 64 bit is supported" }
                    if (useGpuHint.getOrElse(true)) {
                        "win-64.exe"
                    } else {
                        "win-no-gpu-64.exe"
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

            val downloadRoast = tasks.register("downloadRoast$capitalized", Download::class.java) {
                group = GROUP_NAME
                src("https://github.com/fourlastor-alexandria/roast/releases/download/v1.0.0/roast-${target.roastName()}.zip")
                dest(roastZipDir)
                overwrite(false)
            }
            val targetRoastExeDir = baseRoastExeDir.map { it.dir(target.name) }
            val unzipRoast = tasks.register("unzipRoast$capitalized", Copy::class.java) {
                group = GROUP_NAME
                dependsOn(downloadRoast)
                from(project.zipTree(roastZipDir.map { it.file("roast-${target.roastName()}.zip") }))
                into(targetRoastExeDir)
            }

            val targetRoastExeName = pluginExtension.name.flatMap { name ->
                targetRoastExeDir.map { it.file("roast-${target.roastName()}") }.map {
                    if (it.asFile.extension.isEmpty()) {
                        name
                    } else {
                        "$name.exe"
                    }
                }
            }

            val packageRoast = tasks.register("roast$capitalized", RoastTask::class.java) {
                dependsOn(unzipRoast)
                jdkRoot.set(createRuntimeImage.flatMap { it.output })
                appName.set(pluginExtension.name)
                mainClassName.set(mainClass)
                jarFile.set(jarFileLocation)
                vmArgs.set(pluginExtension.roast.vmArgs)
                useZgc.set(pluginExtension.roast.useZgc)
                useMainAsContextClassLoader.set(pluginExtension.roast.useMainAsContextClassLoader)
                output.set(targetRoastDir)
                roastExe.set(targetRoastExeDir.map { it.file("roast-${target.roastName()}") })
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
                        into.set(packageDestination)
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
                        executable.set(pluginExtension.name)
                        identifier.set(target.identifier)
                        icon.set(target.macIcon)
                        outputFile.set(pListFile)
                    }

                    val buildMacAppBundle =
                        tasks.register("buildMacAppBundle$capitalized", BuildMacAppBundle::class.java) {
                            dependsOn(packageRoast, generatePlist)
                            packagedAppDir.set(targetRoastDir)
                            outputDirectory.set(macAppDir)
                            icon.set(target.macIcon)
                            plist.set(pListFile)
                        }

                    tasks.register("package$capitalized", Zip::class.java) {
                        group = GROUP_NAME
                        archiveFileName.set(targetArchiveFileName)
                        destinationDirectory.set(pluginExtension.outputDir)
                        dependsOn(buildMacAppBundle)
                        from(macAppDir)
                        into(packageDestination.flatMap { destination -> pluginExtension.humanName.map { "$destination/$it.app" } })
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
