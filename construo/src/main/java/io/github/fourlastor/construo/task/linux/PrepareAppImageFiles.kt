package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class PrepareAppImageFiles @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : BaseTask() {

    @get:Input abstract val binaryName: Property<String>

    @get:InputDirectory abstract val packagedAppDir: DirectoryProperty

    @get:InputDirectory abstract val templateAppDir: DirectoryProperty

    @get:InputFile abstract val icon: RegularFileProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        fileSystemOperations.copy {
            from(packagedAppDir) {
                rename(binaryName.get(), "AppRun")
            }
            from(templateAppDir)
            if (icon.isPresent) {
                from(icon)
            }
            into(outputDir)
        }
    }
}
