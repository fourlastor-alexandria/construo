package io.github.fourlastor.perceptual

import org.beryx.runtime.RuntimePlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class ConstruoPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply {
            apply(RuntimePlugin::class.java)
        }
    }
}
