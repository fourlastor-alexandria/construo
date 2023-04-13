package io.github.fourlastor.construo

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

class ConstruoPluginExtension(project: Project) {
    val name: Property<String> = project.objects.property(String::class.java)
    val version: Property<String> = project.objects.property(String::class.java)
    val outputDir: DirectoryProperty = project.objects.directoryProperty()
    val winIcon: RegularFileProperty = project.objects.fileProperty()
    val linuxIcon: RegularFileProperty = project.objects.fileProperty()
    val macIcon: RegularFileProperty = project.objects.fileProperty()
}
