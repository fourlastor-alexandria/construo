package io.github.fourlastor.construo.task

import org.gradle.api.file.RegularFile
import java.io.File

/** The local files a target packages, mapped to their path within the distribution. */
internal object PackageFiles {
    private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:")

    fun normalize(label: String, path: String): String {
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

    fun mappings(packageFiles: Map<String, RegularFile>, projectDirectory: File): Map<String, String> =
        packageFiles.mapValues { (_, sourceFile) ->
            val sourcePath = sourceFile.asFile.toPath().toAbsolutePath().normalize()
            val projectPath = projectDirectory.toPath().toAbsolutePath().normalize()
            if (sourcePath.startsWith(projectPath)) {
                projectPath.relativize(sourcePath).toString()
            } else {
                sourcePath.toString()
            }
        }
}
