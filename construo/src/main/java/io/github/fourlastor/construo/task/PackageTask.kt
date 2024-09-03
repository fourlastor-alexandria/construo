package io.github.fourlastor.construo.task

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.IOUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class PackageTask: BaseTask() {

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty
    @get:Input
    abstract val archiveFileName: Property<String>
    @get:InputDirectory
    abstract val from: DirectoryProperty
    @get:Input
    abstract val into: Property<String>
    @get:InputFile
    abstract val executable: RegularFileProperty

    @TaskAction
    fun run() {
        val destination = destinationDirectory.file(archiveFileName).get().asFile
        val baseDir = into.get()
        val executableFile = executable.get().asFile
        ZipArchiveOutputStream(destination.outputStream().buffered()).use { zipOutStream ->
            val inputDir = from.get().asFile
            inputDir.walkTopDown().forEach { inputFile ->
                val entry = ZipArchiveEntry(inputFile, "$baseDir/${inputFile.toRelativeString(inputDir)}".replace(File.separatorChar, '/'))
                if (inputFile.absolutePath == executableFile.absolutePath) {
                    entry.unixMode = "764".toInt(radix = 8)
                }
                zipOutStream.putArchiveEntry(entry)
                if (!inputFile.isDirectory) {
                    IOUtils.copy(inputFile.inputStream(), zipOutStream)
                }
                zipOutStream.closeArchiveEntry()
            }
            zipOutStream.finish()
        }
    }

}
