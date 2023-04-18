package io.github.fourlastor.construo.task.macos

import io.github.fourlastor.construo.task.BaseTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.XmlVersion
import org.redundent.kotlin.xml.xml

abstract class GeneratePlist : BaseTask() {

    @get:Input abstract val humanName: Property<String>

    @get:Optional @get:Input
    abstract val info: Property<String>

    @get:Input abstract val executable: Property<String>

    @get:Input abstract val identifier: Property<String>

    @get:Optional @get:InputFile
    abstract val icon: RegularFileProperty

    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val plist = xml("plist", version = XmlVersion.V10, encoding = "UTF-8") {
            attribute("version", "1.0")
            doctype(
                "plist",
                publicId = "-//Apple Computer//DTD PLIST 1.0//EN",
                systemId = "http://www.apple.com/DTDs/PropertyList-1.0.dtd"
            )
            "dict" {
                "key" { -"CFBundleGetInfoString" }
                "string" { -info.orElse(humanName).get() }
                "key" { -"CFBundleExecutable" }
                "string" { -"bin/${executable.get()}" }
                "key" { -"CFBundleIdentifier" }
                "string" { -identifier.get() }
                "key" { -"CFBundleName" }
                "string" { -humanName.get() }
                if (icon.isPresent) {
                    "key" { -"CFBundleIconFile" }
                    "string" { -icon.get().asFile.name }
                }
                "key" { -"CFBundleShortVersionString" }
                "string" { -"1.0" }
                "key" { -"CFBundleInfoDictionaryVersion" }
                "string" { -"6.0" }
                "key" { -"CFBundlePackageType" }
                "string" { -"APPL" }
                "key" { -"IFMajorVersion" }
                "integer" { -"0" }
                "key" { -"IFMinorVersion" }
                "integer" { -"1" }
                "key" { -"NSHighResolutionCapable" }
                "true" { }
            }
        }
        outputFile.get().asFile.writeText(
            text = plist.toString(printOptions = PrintOptions(pretty = true, singleLineTextElements = true)),
            charset = Charsets.UTF_8
        )
    }
}
