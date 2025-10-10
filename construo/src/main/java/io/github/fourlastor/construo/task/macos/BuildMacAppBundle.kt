package io.github.fourlastor.construo.task.macos

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class BuildMacAppBundle @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : BaseTask() {

    @get:InputDirectory
    abstract val packagedAppDir: DirectoryProperty

    @get:InputFile
    abstract val plist: RegularFileProperty

    @get:InputFile @get:Optional
    abstract val icon: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Optional @get:InputFile abstract val entitlementsFileSource: RegularFileProperty
    @get:OutputFile
    abstract val entitlementsFileDestination: Property<String>


    @TaskAction
    fun run() {
        fileSystemOperations.delete { delete(outputDirectory) }
        fileSystemOperations.copy {
            from(packagedAppDir) {
                includeEmptyDirs = false
                into("MacOS")
                eachFile {
                    if (file.parentFile.name.contains('.')) {
                        val dirSegment = relativePath.segments[relativePath.segments.size - 2]
                        relativePath.segments[relativePath.segments.size - 2] = dirSegment.replace('.','_')
                    }
                }
            }

            from(plist)
            if (icon.isPresent) {
                from(icon) {
                    into("Resources")
                }
            }
            into(
                outputDirectory.get()
                    .dir("Contents")
            )
            if (entitlementsFileSource.isPresent)
            {
                from(entitlementsFileSource)
                into(
                    outputDirectory.get()
                        .dir("Contents")
                )
                rename (entitlementsFileSource.get().asFile.name,entitlementsFileDestination.get() )

            }

        }

    }
}
