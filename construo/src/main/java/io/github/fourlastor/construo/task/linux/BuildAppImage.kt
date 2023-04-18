package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.Architecture
import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class BuildAppImage @Inject constructor(
    private val execOperations: ExecOperations
) : BaseTask() {

    @get:InputDirectory
    abstract val imagesToolsDir: DirectoryProperty

    @get:InputDirectory
    abstract val imageDir: DirectoryProperty

    @get:OutputFile
    abstract val appImageFile: RegularFileProperty

    @get:Input
    abstract val architecture: Property<Architecture>

    @TaskAction
    fun run() {
        execOperations.exec {
            it.setWorkingDir(imagesToolsDir)
            val architecture = architecture.get()
            val arch = architecture.arch
            it.environment(mapOf("ARCH" to arch))
            val appImageTemplateDirPath = imageDir.asFile.get().absolutePath
            val targetPath = appImageFile.asFile.get().absolutePath
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
