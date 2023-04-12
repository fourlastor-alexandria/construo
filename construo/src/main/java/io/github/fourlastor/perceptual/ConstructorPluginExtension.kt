package io.github.fourlastor.perceptual

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

class ConstructorPluginExtension(project: Project) {
    val name: Property<String> = project.objects.property(String::class.java)
    val version: Property<String> = project.objects.property(String::class.java)
    val outputDir: DirectoryProperty = project.objects.directoryProperty()
    val winIcon: RegularFileProperty = project.objects.fileProperty()
    val linuxIcon: RegularFileProperty = project.objects.fileProperty()
    val macIcon: RegularFileProperty = project.objects.fileProperty()
}
