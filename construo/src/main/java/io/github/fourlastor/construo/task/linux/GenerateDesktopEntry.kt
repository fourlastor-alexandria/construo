package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.Architecture
import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateDesktopEntry : BaseTask() {

    @get:Input abstract val humanName: Property<String>

    @get:Input abstract val executable: Property<String>

    @get:Input abstract val version: Property<String>

    @get:Input abstract val architecture: Property<Architecture>

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @get:Optional @get:InputFile
    abstract val icon: RegularFileProperty

    @TaskAction
    fun run() {
        val script = buildString {
            appendLine(
                """
                [Desktop Entry]
                Name=${humanName.get()}
                Exec=${executable.get()}
                X-AppImage-Name${humanName.get()}
                X-AppImage-Version${version.get()}
                X-AppImage-Arch${architecture.get().arch}
                """.trimIndent()
            )
            if (icon.isPresent) {
                appendLine("Icon=${icon.get().asFile.nameWithoutExtension}")
            }
            appendLine(
                """
                Type=Application
                Categories=Game;
                """.trimIndent()
            )
        }
        outputFile.get().asFile.writeText(
            text = script,
            charset = Charsets.UTF_8
        )
    }
}
