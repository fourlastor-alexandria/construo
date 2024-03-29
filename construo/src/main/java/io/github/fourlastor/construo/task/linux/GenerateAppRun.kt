package io.github.fourlastor.construo.task.linux

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateAppRun : BaseTask() {

    @get:Input abstract val executable: Property<String>

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val script = """
                #!/bin/sh
                SELF=${'$'}(readlink -f "${'$'}0")
                HERE=${'$'}{SELF%/*}
                export PATH="${'$'}HERE/bin:${'$'}PATH"
                export ALSOFT_DRIVERS=pulse
                pwd
                cd bin && "./${executable.get()}" "${'$'}@"
        """.trimIndent()
        val file = outputFile.get().asFile
        file.writeText(
            text = script,
            charset = Charsets.UTF_8
        )
        file.setExecutable(true)
    }
}
