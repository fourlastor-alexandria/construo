package io.github.fourlastor.construo.task

import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.IOUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
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
    @get:Optional
    abstract val into: Property<String>
    @get:InputFile
    abstract val executable: RegularFileProperty
    @get:Input
    abstract val packageFiles: MapProperty<String, String>

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getPackageFileInputs(): FileCollection = project.files(packageFiles.keySet())

    @TaskAction
    fun run() {
        val destination = destinationDirectory.file(archiveFileName).get().asFile
        val baseDir = into.orNull
        val executableFile = executable.get().asFile
        val inputDir = from.get().asFile
        val executableParent = executableFile.parentFile.toRelativeString(inputDir).toZipPath()
        val writtenEntries = mutableSetOf<String>()
        ZipArchiveOutputStream(destination.outputStream().buffered()).use { zipOutStream ->
            inputDir.walkTopDown().forEach { inputFile ->
                val relativePath = inputFile.toRelativeString(inputDir).toZipPath()
                val entryName = zipPath(baseDir, relativePath)
                if (entryName.isEmpty()) {
                    return@forEach
                }
                check(writtenEntries.add(entryName)) { "Duplicate package entry: $entryName" }
                val entry = ZipArchiveEntry(inputFile, entryName)
                if (inputFile.absolutePath == executableFile.absolutePath || inputFile.name.startsWith("jspawnhelper") || inputFile.name.startsWith("jexec")) {
                    entry.unixMode = UnixStat.FILE_FLAG or "755".toInt(radix = 8)
                } else {
                    entry.unixMode = if (inputFile.isDirectory) { UnixStat.DIR_FLAG or "755".toInt(radix = 8) } else { UnixStat.FILE_FLAG or "644".toInt(radix = 8) } 
                }
                zipOutStream.putArchiveEntry(entry)
                if (!inputFile.isDirectory) {
                    IOUtils.copy(inputFile.inputStream(), zipOutStream)
                }
                zipOutStream.closeArchiveEntry()
            }
            packageFiles.get().forEach { (sourcePath, destinationPath) ->
                val source = project.file(sourcePath)
                require(source.isFile) { "Package file '$sourcePath' does not exist or is not a regular file" }
                val entryName = zipPath(baseDir, executableParent, destinationPath)
                check(writtenEntries.add(entryName)) { "Duplicate package entry: $entryName" }
                val archiveEntry = ZipArchiveEntry(source, entryName)
                archiveEntry.unixMode = if (source.isDirectory) { UnixStat.DIR_FLAG } else { UnixStat.FILE_FLAG } or "644".toInt(radix = 8)
                zipOutStream.putArchiveEntry(archiveEntry)
                source.inputStream().use { IOUtils.copy(it, zipOutStream) }
                zipOutStream.closeArchiveEntry()
            }
            zipOutStream.finish()
        }
    }

    private fun String.toZipPath(): String = replace(File.separatorChar, '/').trim('/')

    private fun zipPath(vararg parts: String?): String = parts
        .filterNotNull()
        .map { it.toZipPath() }
        .filter { it.isNotEmpty() }
        .joinToString("/")

}
