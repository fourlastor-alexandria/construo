package io.github.fourlastor.construo.task

import io.github.fourlastor.construo.ConstruoPlugin
import org.gradle.api.DefaultTask

abstract class BaseTask : DefaultTask() {
    init {
        group = ConstruoPlugin.GROUP_NAME
    }
}
