package io.github.fourlastor.construo.task.jvm

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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
    private val execOperations: ExecOperations,
) : BaseTask() {

    @get:InputDirectory
    abstract val jdkRoot: DirectoryProperty

    @get:Optional
    @get:InputDirectory
    abstract val targetJdkRoot: DirectoryProperty

    @get:Optional
    @get:Input
    abstract val modules: ListProperty<String>

    @get:Input
    abstract val guessModulesFromJar: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val multiReleaseVersion: Property<String?>

    @get:Input
    abstract val includeDefaultCryptoModules: Property<Boolean>

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
        val modulesFromJar = if (guessModulesFromJar.get()) {
            guessModulesFromJar(javaHome)
        } else {
            emptyList()
        }
        val defaultCryptoModules = if (includeDefaultCryptoModules.get()) {
            listOf("jdk.crypto.ec")
        } else {
            emptyList()
        }
        val modulesList = (
                modules.getOrElse(emptyList())
                + modulesFromJar
                + defaultCryptoModules
        ).distinct()

        execOperations.exec {
            val root = if (targetJdkRoot.isPresent) {
                targetJdkRoot
            } else {
                jdkRoot
            }.map { it.dir("jmods") }
            val modulesPath = root.get().asFile.absolutePath
            val addModulesArg = if (modulesList.isNotEmpty()) {
                val modulesCommaSeparated = modulesList.joinToString(separator = ",")
                arrayOf(
                    "--add-modules",
                    modulesCommaSeparated
                )
            } else {
                arrayOf()
            }
            val jlinkArgs = arrayOf(
                "--no-header-files",
                "--strip-native-commands",
                "--no-man-pages",
                "--compress=1",
                "--strip-debug"
            ) + addModulesArg + arrayOf(
                "--module-path",
                modulesPath,
                "--output",
                outputFile.absolutePath
            )
            commandLine(
                File(javaHome, executableForOs("bin/jlink")).absolutePath,
                *jlinkArgs
            )
        }
    }

	private fun guessModulesFromJar(javaHome: File): List<String> = executeInJdk(
		javaHome,
		"jdeps",
		"--ignore-missing-deps",
		"--multi-release", multiReleaseVersion.orNull ?: getJvmVersion(javaHome),
		"--list-deps",
		jarFile.get().asFile.absolutePath
	).splitToSequence("\n").map { it.trim() }.filter { it.isNotBlank() }.toList()

	private fun getJvmVersion(javaHome: File): String = executeInJdk(javaHome, "java", "-version")
		.split(" ")[2].removePrefix("\"").split(".")[0]

	private fun executeInJdk(javaHome: File, command: String, vararg args: String): String = ByteArrayOutputStream().use {
		execOperations.exec {
			setWorkingDir(jdkRoot)
			standardOutput = it
			errorOutput = it // java version goes to the error stream
			commandLine(
				File(javaHome, executableForOs("bin/$command")).absolutePath,
				*args
			)
		}
		it.toByteArray().toString(Charset.defaultCharset())
	}

    private fun executableForOs(executable: String): String = OperatingSystem.current().let { currentOs ->
        if (currentOs.isWindows) {
            "$executable.exe"
        } else executable
    }
}
