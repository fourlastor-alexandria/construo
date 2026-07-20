package io.github.fourlastor.construo.task

import org.apache.commons.compress.archivers.zip.UnixStat
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.io.IOUtils
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class PackageTask @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : BaseTask() {

    @get:Internal
    abstract val destinationDirectory: DirectoryProperty
    @get:Internal
    abstract val archiveFileName: Property<String>
    @get:OutputFile
    abstract val archiveFile: RegularFileProperty
    @get:InputDirectory
    abstract val from: DirectoryProperty
    @get:Input
    @get:Optional
    abstract val into: Property<String>
    @get:InputFile
    abstract val executable: RegularFileProperty
    @get:Internal
    abstract val packageFiles: MapProperty<String, RegularFile>

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getPackageFileInputs(): FileCollection = objectFactory.fileCollection().from(packageFiles.map { it.values })

    @Input
    fun getPackageFileMappings(): Map<String, String> = packageFiles.get().mapValues { (_, sourceFile) ->
        val sourcePath = sourceFile.asFile.toPath().toAbsolutePath().normalize()
        val projectPath = projectLayout.projectDirectory.asFile.toPath().toAbsolutePath().normalize()
        if (sourcePath.startsWith(projectPath)) {
            projectPath.relativize(sourcePath).toString()
        } else {
            sourcePath.toString()
        }
    }

    @TaskAction
    fun run() {
        val baseDir = into.orNull
            ?.takeUnless { it.isBlank() }
            ?.let { normalizeArchivePath("Archive root", it) }
        val packageFileMappings = packageFiles.get().map { (destinationPath, sourceFile) ->
            normalizeArchivePath("Package file destination", destinationPath) to sourceFile
        }
        val destination = archiveFile.get().asFile
        val executableFile = executable.get().asFile
        val inputDir = from.get().asFile
        val destinationPath = destination.canonicalFile.toPath()
        val inputPath = inputDir.canonicalFile.toPath()
        require(!destinationPath.startsWith(inputPath)) {
            "Archive output '$destination' must not be inside package input '$inputDir'"
        }
        packageFileMappings.forEach { (_, sourceFile) ->
            require(destinationPath != sourceFile.asFile.canonicalFile.toPath()) {
                "Archive output '$destination' must not overwrite package input '${sourceFile.asFile}'"
            }
        }
        destination.parentFile.mkdirs()
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
            packageFileMappings.forEach { (destinationPath, sourceFile) ->
                val source = sourceFile.asFile
                require(source.isFile) { "Package file '$source' does not exist or is not a regular file" }
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

    private fun String.toZipPath(): String = replace('\\', '/').trim('/')

    private fun normalizeArchivePath(label: String, path: String): String {
        val normalized = path.replace('\\', '/')
        require(
            normalized.isNotBlank() &&
                !normalized.startsWith('/') &&
                !WINDOWS_ABSOLUTE_PATH.containsMatchIn(normalized) &&
                normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }
        ) {
            "$label '$path' must be a normalized relative path within the distribution"
        }
        return normalized
    }

    private fun zipPath(vararg parts: String?): String = parts
        .filterNotNull()
        .map { it.toZipPath() }
        .filter { it.isNotEmpty() }
        .joinToString("/")

    companion object {
        private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:")
    }

}
