package io.github.fourlastor.construo

import io.github.fourlastor.construo.task.DownloadTask
import io.github.fourlastor.construo.task.ExtractJdkTask
import io.github.fourlastor.construo.task.PackageTask
import io.github.fourlastor.construo.task.VerifySha256Task
import io.github.fourlastor.construo.task.jvm.CreateRuntimeImageTask
import io.github.fourlastor.construo.task.jvm.RoastTask
import io.github.fourlastor.construo.task.macos.BuildMacAppBundle
import io.github.fourlastor.construo.task.macos.ExtractRcodesign
import io.github.fourlastor.construo.task.macos.GeneratePlist
import io.github.fourlastor.construo.task.macos.NotarizeMacAppBundle
import io.github.fourlastor.construo.task.macos.Rcodesign
import io.github.fourlastor.construo.task.macos.SignMacAppBundle
import io.github.fourlastor.construo.task.windows.ReplaceWinIconTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
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
        val filename: String,
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

        // rcodesign signs macOS app bundles: its build for this host is downloaded once, unless one is given
        val rcodesignOptions = pluginExtension.rcodesign
        val rcodesignDir = baseBuildDir.map { it.dir("rcodesign") }
        val rcodesignTarget = project.provider { Rcodesign.hostTarget() }
        rcodesignOptions.sha256.convention(
            rcodesignOptions.version.zip(rcodesignTarget) { version, target -> version to target }.flatMap { (version, target) ->
                Rcodesign.defaultSha256(version, target)
                    ?.let { project.provider { it } }
                    ?: project.objects.property(String::class.java)
            },
        )
        val downloadRcodesign = tasks.register("downloadRcodesign", DownloadTask::class.java) {
            src.set(
                rcodesignOptions.baseUrl.zip(rcodesignOptions.version) { baseUrl, version -> baseUrl to version }
                    .zip(rcodesignTarget) { (baseUrl, version), target -> Rcodesign.url(baseUrl, version, target) },
            )
            dest.set(
                rcodesignDir.zip(rcodesignOptions.version.zip(rcodesignTarget, Rcodesign::archiveName)) { dir, archive ->
                    dir.file(archive)
                },
            )
        }
        val verifyRcodesign = tasks.register("verifyRcodesign", VerifySha256Task::class.java) {
            archive.set(downloadRcodesign.flatMap { it.dest })
            expectedSha256.set(rcodesignOptions.sha256)
        }
        val extractRcodesign = tasks.register("extractRcodesign", ExtractRcodesign::class.java) {
            dependsOn(rcodesignOptions.sha256.map { listOf(verifyRcodesign) }.orElse(emptyList()))
            archive.set(downloadRcodesign.flatMap { it.dest })
            outputDirectory.set(rcodesignDir.map { it.dir("bin") })
        }
        val rcodesignExecutable = rcodesignOptions.executable.orElse(rcodesignDir.map { it.dir("bin").file("rcodesign") })
        val rcodesignTasks = rcodesignOptions.executable.map { emptyList<TaskProvider<*>>() }.orElse(listOf(extractRcodesign))

        // Register the correct tasks for each target
        pluginExtension.targets.all {
            val target = this
            target.prePackageTasks.convention(emptyList())
            target.packageFiles.convention(emptyMap())
            target.packagingToolJdk.convention(Target.PackagingToolJdk.HOST_JDK)
            if (target is Target.MacOs) {
                target.appBundle.convention(true)
                target.notarization.enabled.convention(false)
                target.signing.enabled.convention(target.notarization.enabled)
                target.signing.hardenedRuntime.convention(target.signing.certificate.map { true }.orElse(false))
            }
            val targetBuildDir = baseBuildDir.map { it.dir(target.name) }
            val targetRuntimeImageBuildDir = baseRuntimeImageBuildDir.map { it.dir("${project.name}-${target.name}") }
            val targetRuntimeImageWithNativeCommandsBuildDir = baseRuntimeImageBuildDir.map { it.dir("${project.name}-${target.name}-natives") }

            val capitalized = target.name.replaceFirstChar(Char::uppercase)
            val targetArchiveFileName = pluginExtension.name.map { "$it-${target.name}.zip" }

            val jdkUrl = target.jdkUrl.map {
                val extension = if (it.endsWith(".zip")) "zip" else "tar.gz"
                DownloadJdkOptions(
                    url = it,
                    filename = "${target.name}.$extension",
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
            val verifyJdk =
                tasks.register("verifyJdk$capitalized", VerifySha256Task::class.java) {
                    group = GROUP_NAME
                    archive.set(downloadJdk.flatMap { it.dest })
                    expectedSha256.set(target.jdkSha256)
                }
            val targetJdkDir = jdkDir.map { it.dir(target.name) }
            val unzipJdk = tasks.register("unzipJdk$capitalized", ExtractJdkTask::class.java) {
                group = GROUP_NAME
                dependsOn(target.jdkSha256.map { listOf(verifyJdk) }.orElse(emptyList()))
                archive.set(downloadJdk.flatMap { it.dest })
                outputDirectory.set(targetJdkDir)
            }
            val targetRoastDir = targetBuildDir.map { it.dir("roast") }

            val runningJdkRoot = pluginExtension.jdkRoot.orElse(project.layout.dir(project.provider { File(System.getProperty("java.home")) }))
            val jdkTargetRoot = unzipJdk.flatMap { it.outputDirectory }
            val packagingJdkRoot = target.packagingToolJdk.flatMap {
                if (it == Target.PackagingToolJdk.TARGET_JDK) jdkTargetRoot else runningJdkRoot
            }

            val selectedTask = pluginExtension.jarTask
                .map { tasks.getByName(it) }
                .orElse(project.provider { (tasks.findByName("shadowJar") ?: tasks.findByName("jar")) })

            val mainClass = pluginExtension.mainClass.orElse(
                selectedTask.map {
                    if (it is Jar) {
                        it.manifest.attributes["Main-Class"] as String
                    } else {
                        throw IllegalStateException("Setting a custom task which is not a Jar task requires manually setting the mainClass property")
                    }
                },
            )

            val jarFileLocation = selectedTask.flatMap {
                when (it) {
                    is Jar -> it.archiveFile
                    is ProGuardTask -> project.layout.file(project.provider { it.outJarFileCollection.first() })
                    else -> throw IllegalStateException("Only supported custom task types are Jar and ProguardTask, it was ${it.javaClass}")
                }
            }

            fun createRuntimeImageTask(
                name: String,
                withoutNativeCommands: Boolean,
                targetRuntimeImageBuildDir: Provider<Directory>,
            ) = tasks.register(name, CreateRuntimeImageTask::class.java) {
                dependsOn(unzipJdk, selectedTask)
                jdkRoot.set(packagingJdkRoot)
                jarFile.set(jarFileLocation)
                targetJdkRoot.set(jdkTargetRoot)
                modules.set(pluginExtension.jlink.modules)
                includeDefaultCryptoModules.set(pluginExtension.jlink.includeDefaultCryptoModules)
                guessModulesFromJar.set(pluginExtension.jlink.guessModulesFromJar)
                multiReleaseVersion.set(pluginExtension.jlink.multiReleaseVersion)
                output.set(targetRuntimeImageBuildDir)
                stripNativeCommands.set(withoutNativeCommands)
            }

            val createRuntimeImage = createRuntimeImageTask(
                "createRuntimeImage$capitalized",
                withoutNativeCommands = true,
                targetRuntimeImageBuildDir,
            )
            createRuntimeImageTask(
                "createRuntimeImageWithNatives$capitalized",
                withoutNativeCommands = false,
                targetRuntimeImageWithNativeCommandsBuildDir,
            )

            fun Target.roastName(): Provider<String> = when (this) {
                is Target.Windows -> {
                    architecture.zip(useConsole.orElse(false)) { architecture, useConsole ->
                        architecture to useConsole
                    }.zip(useGpuHint.orElse(true)) { (architecture, useConsole), useGpuHint ->
                        val architectureSuffix = when (architecture) {
                            Target.Architecture.X86_64 -> "x86_64"
                            Target.Architecture.AARCH64 -> "aarch64"
                            else -> error("Unsupported architecture: $architecture")
                        }
                        if (useConsole) {
                            "win-console-$architectureSuffix.exe"
                        } else if (useGpuHint) {
                            "win-$architectureSuffix.exe"
                        } else {
                            "win-no-gpu-$architectureSuffix.exe"
                        }
                    }
                }

                is Target.Linux -> architecture.map {
                    when (it) {
                        Target.Architecture.X86_64 -> "linux-x86_64"
                        Target.Architecture.AARCH64 -> "linux-aarch64"
                    }
                }

                is Target.MacOs -> architecture.map {
                    when (it) {
                        Target.Architecture.X86_64 -> "macos-x86_64"
                        Target.Architecture.AARCH64 -> "macos-aarch64"
                    }
                }

                else -> error("Unsupported target.")
            }

            val roastName = target.roastName()
            val defaultRoastUrl =
                pluginExtension.roast.baseUrl
                    .zip(pluginExtension.roast.version) { baseUrl, version ->
                        "${baseUrl.trimEnd('/')}/$version"
                    }
                    .zip(roastName) { releaseUrl, name -> "$releaseUrl/roast-$name.zip" }
            target.roastUrl.convention(defaultRoastUrl)
            val downloadRoast = tasks.register("downloadRoast$capitalized", DownloadTask::class.java) {
                group = GROUP_NAME
                src.set(target.roastUrl)
                dest.set(roastZipDir.map { it.file("${target.name}/roast.zip") })
            }
            val verifyRoast =
                tasks.register("verifyRoast$capitalized", VerifySha256Task::class.java) {
                    group = GROUP_NAME
                    archive.set(downloadRoast.flatMap { it.dest })
                    expectedSha256.set(target.roastSha256)
                }
            val targetRoastExeDir = baseRoastExeDir.map { it.dir(target.name) }
            val unzipRoast = tasks.register("unzipRoast$capitalized", Copy::class.java) {
                group = GROUP_NAME
                dependsOn(target.roastSha256.map { listOf(verifyRoast) }.orElse(emptyList()))
                from(project.zipTree(downloadRoast.flatMap { it.dest }))
                into(targetRoastExeDir)
            }

            val packageDependencies = mutableListOf<TaskProvider<*>>(unzipRoast)
            val targetRoastExeDirWithIcon = targetRoastExeDir.map { it.dir("icon") }
            val originalRoastExe =
                targetRoastExeDir.flatMap { directory ->
                    directory.file(roastName.map { "roast-$it" })
                }
            val targetRoastExe = if (target is Target.Windows) {
                targetRoastExeDirWithIcon
            } else {
                targetRoastExeDir
            }.flatMap { directory -> directory.file(roastName.map { "roast-$it" }) }

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

            val packageExtraFiles = pluginExtension.packageFiles.zip(target.packageFiles) { generic, targetSpecific ->
                generic + targetSpecific
            }

            when (target) {
                is Target.Linux, is Target.Windows -> {
                    tasks.register("package$capitalized", PackageTask::class.java) {
                        group = GROUP_NAME
                        dependsOn(packageRoast, pluginExtension.prePackageTasks, target.prePackageTasks)
                        archiveFileName.set(targetArchiveFileName)
                        destinationDirectory.set(pluginExtension.outputDir)
                        archiveFile.convention(
                            target.archiveFile.orElse(destinationDirectory.file(archiveFileName)),
                        )
                        from.set(targetRoastDir)
                        into.set(pluginExtension.zipFolder)
                        executable.set(targetRoastDir.flatMap { it.file(targetRoastExeName) })
                        packageFiles.set(packageExtraFiles)
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

                    val entitlementsFileName = pluginExtension.humanName.map { "$it.entitlements" }
                    val signing = target.signing
                    val notarization = target.notarization

                    val buildMacAppBundle =
                        tasks.register("buildMacAppBundle$capitalized", BuildMacAppBundle::class.java) {
                            dependsOn(packageRoast, generatePlist)
                            packagedAppDir.set(targetRoastDir)
                            outputDirectory.set(macAppDir)
                            icon.set(target.macIcon)
                            // a signature carries the entitlements, and a file at the root of Contents is left out of its seal
                            entitlementsFileSource.set(
                                signing.enabled.flatMap { if (it) project.objects.fileProperty() else target.entitlementsFile },
                            )
                            entitlementsFileDestination.set(entitlementsFileName)
                            plist.set(pListFile)
                        }

                    val signedAppDir = targetBuildDir.flatMap { dir ->
                        pluginExtension.humanName.map { dir.dir("signed/$it.app") }
                    }
                    val signingEnabled = signing.enabled
                    val signMacAppBundle =
                        tasks.register("signMacAppBundle$capitalized", SignMacAppBundle::class.java) {
                            onlyIf { signingEnabled.get() }
                            dependsOn(buildMacAppBundle, rcodesignTasks, pluginExtension.prePackageTasks, target.prePackageTasks)
                            appDirectory.set(macAppDir)
                            executableName.set(targetRoastExeName)
                            rcodesign.set(rcodesignExecutable)
                            certificate.set(signing.certificate)
                            certificatePassword.set(signing.certificatePassword)
                            entitlementsFile.set(target.entitlementsFile)
                            hardenedRuntime.set(signing.hardenedRuntime)
                            forNotarization.set(notarization.enabled)
                            packageFiles.set(packageExtraFiles)
                            outputDirectory.set(signedAppDir)
                        }

                    val notarizedAppDir = targetBuildDir.flatMap { dir ->
                        pluginExtension.humanName.map { dir.dir("notarized/$it.app") }
                    }
                    val notarizationEnabled = notarization.enabled
                    val notarizeMacAppBundle =
                        tasks.register("notarizeMacAppBundle$capitalized", NotarizeMacAppBundle::class.java) {
                            onlyIf { notarizationEnabled.get() }
                            dependsOn(signMacAppBundle, rcodesignTasks)
                            appDirectory.set(signedAppDir)
                            rcodesign.set(rcodesignExecutable)
                            issuerId.set(notarization.issuerId)
                            keyId.set(notarization.keyId)
                            privateKeyFile.set(notarization.privateKeyFile)
                            outputDirectory.set(notarizedAppDir)
                        }

                    // what's packaged: the raw layout, or the app bundle as built, signed or notarized
                    fun <T : Any> byStage(raw: T, built: T, signed: T, notarized: T): Provider<T> =
                        target.appBundle.zip(signing.enabled) { appBundle, sign -> appBundle to sign }
                            .zip(notarization.enabled) { (appBundle, sign), notarize ->
                                check(appBundle || !sign) {
                                    "Target ${target.name} signs its app, which needs appBundle = true"
                                }
                                check(sign || !notarize) {
                                    "Target ${target.name} notarizes its app, which needs signing.enabled = true"
                                }
                                when {
                                    !appBundle -> raw
                                    notarize -> notarized
                                    sign -> signed
                                    else -> built
                                }
                            }
                    val packagedDir = byStage(targetRoastDir, macAppDir, signedAppDir, notarizedAppDir).flatMap { it }

                    tasks.register("package$capitalized", PackageTask::class.java) {
                        group = GROUP_NAME
                        archiveFileName.set(targetArchiveFileName)
                        destinationDirectory.set(pluginExtension.outputDir)
                        archiveFile.convention(
                            target.archiveFile.orElse(destinationDirectory.file(archiveFileName)),
                        )
                        dependsOn(
                            byStage<TaskProvider<*>>(packageRoast, buildMacAppBundle, signMacAppBundle, notarizeMacAppBundle),
                            pluginExtension.prePackageTasks,
                            target.prePackageTasks,
                        )
                        from.set(packagedDir)
                        into.set(
                            target.appBundle.flatMap { appBundle ->
                                if (appBundle) {
                                    pluginExtension.humanName.flatMap { humanName ->
                                        if (pluginExtension.zipFolder.isPresent) {
                                            pluginExtension.zipFolder.map { destination ->
                                                "$destination/$humanName.app"
                                            }
                                        } else {
                                            project.provider { "$humanName.app" }
                                        }
                                    }
                                } else {
                                    pluginExtension.zipFolder
                                }
                            },
                        )
                        executable.set(
                            target.appBundle.flatMap { appBundle ->
                                packagedDir.flatMap { directory ->
                                    if (appBundle) {
                                        directory.dir("Contents").dir("MacOS").file(targetRoastExeName)
                                    } else {
                                        directory.file(targetRoastExeName)
                                    }
                                }
                            },
                        )
                        // a signed bundle has them already, within its seal
                        packageFiles.set(
                            signing.enabled.flatMap { if (it) project.provider { emptyMap<String, RegularFile>() } else packageExtraFiles },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val GROUP_NAME = "construo"
    }
}
