package io.github.fourlastor.construo.task.macos

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.*
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import javax.inject.Inject

abstract class BuildMacAppBundle @Inject constructor(
    private val fileSystemOperations: FileSystemOperations
) : BaseTask() {

    @get:InputDirectory abstract val packagedAppDir: DirectoryProperty

    @get:InputFile abstract val plist: RegularFileProperty

    @get:InputFile @get:Optional
    abstract val icon: RegularFileProperty

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

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
