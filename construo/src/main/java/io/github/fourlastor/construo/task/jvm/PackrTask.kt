package io.github.fourlastor.construo.task.jvm

import com.badlogicgames.packr.Packr
import com.badlogicgames.packr.PackrConfig
import io.github.fourlastor.construo.Target
import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class PackrTask : BaseTask() {

    private val packr = Packr()

    @get:InputDirectory
    abstract val jdkRoot: DirectoryProperty

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val mainClassName: Property<String>

    @get:Input
    abstract val packingTarget: Property<Target>

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
        val config = PackrConfig()
        config.platform = packingTarget.get().toPackrPlatform()
        config.jdk = jdkRoot.get().asFile.absolutePath
        config.executable = appName.get()
        config.classpath = listOf(jarFile.get().asFile.absolutePath)
        config.removePlatformLibs = config.classpath
        config.mainClass = mainClassName.get()
        config.vmArgs = listOf("Xmx1G")
        config.outDir = outputFile
        config.useZgcIfSupportedOs = true
        packr.pack(config)
    }

    private fun Target.toPackrPlatform() = when (this) {
        is Target.Windows -> PackrConfig.Platform.Windows64
        is Target.Linux -> PackrConfig.Platform.Linux64
        is Target.MacOs -> PackrConfig.Platform.MacOS
        else -> throw IllegalArgumentException("Target not recognized")
    }
}
