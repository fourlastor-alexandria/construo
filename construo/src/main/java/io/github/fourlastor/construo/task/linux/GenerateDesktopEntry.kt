package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateDesktopEntry : BaseTask() {

    @get:Input abstract val name: Property<String>

    @get:Input abstract val executable: Property<String>

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @get:Optional @get:InputFile
    abstract val icon: RegularFileProperty

    @TaskAction
    fun run() {
        val script = buildString {
            appendLine(
                """
                [Desktop Entry]
                Name=${name.get()}
                Exec=${executable.get()}
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
