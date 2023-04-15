package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

abstract class PrepareAppImageTools @Inject constructor(
    private val execOperations: ExecOperations
): BaseTask() {

    @get:InputDirectory
    abstract val imagesToolsDir: DirectoryProperty

    @TaskAction
    fun run() {
        execOperations.exec {
            it.setWorkingDir(imagesToolsDir)
            it.commandLine("chmod", "+x", "appimagetool-x86_64.AppImage")
        }
    }
}
