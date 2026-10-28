package io.github.fourlastor.construo.task.macos

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ExtractRcodesign @Inject constructor(
    private val archiveOperations: ArchiveOperations,
    private val fileSystemOperations: FileSystemOperations,
) : BaseTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val archiveFile = archive.get().asFile
        val executable = archiveOperations.tarTree(archiveFile)
            .matching { include("**/rcodesign") }
            .singleOrNull()
            ?: error("Couldn't find rcodesign in ${archiveFile.absolutePath}")
        fileSystemOperations.sync {
            from(executable)
            into(outputDirectory)
        }
        outputDirectory.get().file(executable.name).asFile.setExecutable(true)
    }
}
