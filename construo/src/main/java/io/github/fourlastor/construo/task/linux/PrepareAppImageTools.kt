package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction

abstract class PrepareAppImageTools : BaseTask() {

    @get:InputDirectory
    abstract val imagesToolsDir: DirectoryProperty

    @TaskAction
    fun run() {
        imagesToolsDir.get().file("appimagetool-x86_64.AppImage").asFile.setExecutable(true)
    }
}
