package io.github.fourlastor.construo.task.jvm

import io.github.fourlastor.construo.task.BaseTask
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject

abstract class RoastTask @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : BaseTask() {

    @get:InputDirectory
    abstract val jdkRoot: DirectoryProperty

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val mainClassName: Property<String>

    @get:InputFile
    abstract val roastExe: RegularFileProperty

    @get:Input
    abstract val roastExeName: Property<String>

    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:Input
    abstract val runOnFirstThread: Property<Boolean>

    @get:Input
    abstract val vmArgs: ListProperty<String>

    @get:Input
    abstract val useZgc: Property<Boolean>

    @get:Input
    abstract val useMainAsContextClassLoader: Property<Boolean>

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun run() {
        val outputDir = output.get().asFile
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
        }

        outputDir.mkdirs()

        val configDir = File(outputDir, "app").apply { mkdirs() }

        File(configDir, "${appName.get()}.json").writer().buffered().use {
            it.write(
                Json.encodeToString(
                    PackConfig(
                        listOf(jarFile.get().asFile.name),
                        mainClass = mainClassName.get(),
                        runOnFirstThread = runOnFirstThread.get(),
                        vmArgs = vmArgs.get(),
                        useZgcIfSupportedOs = useZgc.get(),
                        useMainAsContextClassLoader = useMainAsContextClassLoader.get()
                    )
                )
            )
        }

        fileSystemOperations.copy {
            from(roastExe)
            rename { roastExeName.get() }
            into(output)
        }

        fileSystemOperations.copy {
            from(jdkRoot)
            into(output.dir("runtime"))
        }

        fileSystemOperations.copy {
            from(jarFile)
            into(output)
        }
    }

    @Serializable
    private data class PackConfig(
        val classPath: List<String>,
        val mainClass: String,
        val runOnFirstThread: Boolean,
        val useZgcIfSupportedOs: Boolean,
        val useMainAsContextClassLoader: Boolean,
        val vmArgs: List<String>
    )
}
