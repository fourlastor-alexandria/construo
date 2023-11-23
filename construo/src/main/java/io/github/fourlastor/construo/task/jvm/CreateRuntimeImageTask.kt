package io.github.fourlastor.construo.task.jvm

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import javax.inject.Inject

abstract class CreateRuntimeImageTask @Inject constructor(
    private val execOperations: ExecOperations
) : BaseTask() {

    @get:InputDirectory
    abstract val jdkRoot: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    abstract val targetJdkRoot: DirectoryProperty

    @get:Optional
    @get:Input
    abstract val modules: ListProperty<String>

    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @TaskAction
    fun run() {
        val outputFile = output.get().asFile
        if (outputFile.exists()) {
            outputFile.deleteRecursively()
        }
        val javaExecName = executableForOs("bin/java")
        val javaHome = jdkRoot.asFile.get().walkTopDown()
            .first { File(it, javaExecName).isFile }
        val modulesList = modules.get().takeUnless { it.isEmpty() } ?: guessModulesFromJar(javaHome)
        execOperations.exec {
            val modulesCommaSeparated = modulesList.joinToString(separator = ",")
            val root = if (targetJdkRoot.isPresent) {
                targetJdkRoot
            } else {
                jdkRoot
            }.map { it.dir("jmods") }
            val modulesPath = root.get().asFile.absolutePath
            commandLine(
                File(javaHome, executableForOs("bin/jlink")).absolutePath,
                "--no-header-files",
                "--strip-native-commands",
                "--no-man-pages",
                "--compress=1",
                "--strip-debug",
                "--add-modules",
                modulesCommaSeparated,
                "--module-path",
                modulesPath,
                "--output",
                outputFile.absolutePath
            )
        }
    }

    private fun guessModulesFromJar(javaHome: File): List<String> = ByteArrayOutputStream().use {
        execOperations.exec {
            setWorkingDir(jdkRoot)
            standardOutput = it
            commandLine(
                File(javaHome, executableForOs("bin/jdeps")).absolutePath,
                "--ignore-missing-deps",
                "--list-deps",
                jarFile.get().asFile.absolutePath
            )
        }
        it.toByteArray().toString(Charset.defaultCharset())
    }.splitToSequence("\n").map { it.trim() }.toList()

    private fun executableForOs(executable: String): String = OperatingSystem.current().let { currentOs ->
        if (currentOs.isWindows) {
            "$executable.exe"
        } else executable
    }
}
