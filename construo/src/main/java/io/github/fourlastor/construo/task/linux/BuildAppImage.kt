package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.Architecture
import io.github.fourlastor.construo.ConstruoPlugin
import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class BuildAppImage @Inject constructor(
    private val execOperations: ExecOperations
) : BaseTask() {

    @get:InputDirectory
    abstract val imagesToolsDir: DirectoryProperty

    @get:InputDirectory
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
    abstract val targetName: Property<String>

    @get:Input
    abstract val architecture: Property<Architecture>

    @TaskAction
    fun run() {
        execOperations.exec {
            it.setWorkingDir(imagesToolsDir)
            val architecture = architecture.get()
            val arch = architecture.arch
            it.environment(mapOf("ARCH" to arch))
            val appImageTemplateDirPath = inputDir.get().dir(targetName.get()).dir(ConstruoPlugin.APP_DIR_NAME).asFile.absolutePath
            val targetPath = outputDir.get().file("${ConstruoPlugin.APP_IMAGE_NAME}-${targetName.get()}").asFile.absolutePath
            it.commandLine(
                "./appimagetool-x86_64.AppImage",
                "-n",
                appImageTemplateDirPath,
                targetPath,
                "--runtime-file",
                "./${runtimeName(architecture)}"
            )
        }
    }

    private fun runtimeName(arch: Architecture): String = when (arch) {
        Architecture.AARCH64 -> "runtime-aarch64"
        Architecture.X64 -> "runtime-x86_64"
    }
}
