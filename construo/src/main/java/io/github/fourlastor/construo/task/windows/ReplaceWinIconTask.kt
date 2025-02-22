package io.github.fourlastor.construo.task.windows

import io.github.fourlastor.construo.editpe.EditPE
import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ReplaceWinIconTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations,
): BaseTask() {

    @get:InputFile
    abstract val inputFile: RegularFileProperty
    @get:InputFile
    @get:Optional
    abstract val inputIcon: RegularFileProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        if (!inputIcon.isPresent) {
            fileSystemOperations.copy {
                from(inputFile)
                into(outputFile.get().asFile.parentFile)
            }
        } else {
            EditPE.replaceIcon(
                inputFile.get().asFile.absolutePath,
                inputIcon.get().asFile.absolutePath,
                outputFile.get().asFile.absolutePath,
            )
        }
    }
}
