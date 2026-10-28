package io.github.fourlastor.construo.task.macos

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.inject.Inject

/**
 * Has Apple notarize a signed app bundle, waiting for its verdict, then staples the ticket to the
 * bundle, so Gatekeeper accepts it offline too.
 */
abstract class NotarizeMacAppBundle @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
) : BaseTask() {

    @get:InputDirectory
    abstract val appDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rcodesign: RegularFileProperty

    // the App Store Connect API key: credentials, which aren't recorded with the task's inputs
    @get:Internal
    abstract val issuerId: Property<String>

    @get:Internal
    abstract val keyId: Property<String>

    @get:Internal
    abstract val privateKeyFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        check(issuerId.isPresent && keyId.isPresent && privateKeyFile.isPresent) {
            "Notarization needs an App Store Connect API key: set notarization.issuerId, notarization.keyId " +
                "and notarization.privateKeyFile"
        }
        val app = outputDirectory.get().asFile
        fileSystemOperations.delete { delete(app) }
        copyWithLinks(appDirectory.get().asFile.toPath(), app.toPath())

        val apiKey = File(temporaryDir, "api-key.json")
        try {
            rcodesign(
                "encode-app-store-connect-api-key",
                "--output-path",
                apiKey.absolutePath,
                issuerId.get(),
                keyId.get(),
                privateKeyFile.get().asFile.absolutePath,
            )
            rcodesign("notary-submit", "--api-key-file", apiKey.absolutePath, "--staple", app.absolutePath)
        } finally {
            apiKey.delete()
        }
    }

    private fun rcodesign(vararg args: String) {
        val result = execOperations.runRcodesign(rcodesign.get().asFile, temporaryDir, args.toList())
        if (result.exitValue != 0) {
            throw GradleException("rcodesign ${args.first()} failed with exit value ${result.exitValue}")
        }
    }

    // the links of a signed bundle are sealed as links, so they must not become copies of what they lead to
    private fun copyWithLinks(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val copy = target.resolve(source.relativize(path).toString())
                when {
                    Files.isSymbolicLink(path) -> Files.createSymbolicLink(copy, Files.readSymbolicLink(path))
                    Files.isDirectory(path) -> Files.createDirectories(copy)
                    else -> Files.copy(path, copy, StandardCopyOption.COPY_ATTRIBUTES)
                }
            }
        }
    }
}
