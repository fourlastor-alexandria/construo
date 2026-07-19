package io.github.fourlastor.construo.task

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import javax.inject.Inject

abstract class ExtractJdkTask : BaseTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun run() {
        val archiveFile = archive.get().asFile
        val stagingDirectory = temporaryDir.resolve("archive")
        deleteTree(stagingDirectory)
        val archiveTree: FileTree = if (archiveFile.extension == "zip") {
            archiveOperations.zipTree(archiveFile)
        } else {
            archiveOperations.tarTree(archiveFile)
        }
        fileSystemOperations.copy {
            from(archiveTree)
            into(stagingDirectory)
        }

        val jdkRoot = stagingDirectory
            .walkTopDown()
            .firstOrNull { File(it, "bin/java").isFile || File(it, "bin/java.exe").isFile }
            ?: error("Couldn't find java home in ${archiveFile.absolutePath}")
        val destination = outputDirectory.get().asFile
        deleteTree(destination)
        fileSystemOperations.copy {
            from(jdkRoot)
            into(destination)
        }
    }

    private fun deleteTree(directory: File) {
        if (!directory.exists()) return
        Files.walk(directory.toPath()).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
        check(!directory.exists()) { "Could not completely remove '${directory.absolutePath}'" }
    }
}
