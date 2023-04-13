package io.github.fourlastor.construo

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

interface ConstruoPluginExtension {
    val name: Property<String>
    val version: Property<String>
    val outputDir: DirectoryProperty
    val winIcon: RegularFileProperty
    val linuxIcon: RegularFileProperty
    val macIcon: RegularFileProperty
}
