package io.github.fourlastor.construo

import org.gradle.api.Action
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ConstruoPluginExtension @Inject constructor(
    objectFactory: ObjectFactory
) {
    abstract val name: Property<String>
    abstract val humanName: Property<String>
    abstract val info: Property<String>
    abstract val version: Property<String>
    abstract val outputDir: DirectoryProperty
    abstract val jdkRoot: DirectoryProperty
    abstract val mainClass: Property<String>
    val targets: ExtensiblePolymorphicDomainObjectContainer<Target> = objectFactory.polymorphicDomainObjectContainer(Target::class.java)
    val jlink: JlinkOptions = objectFactory.newInstance(JlinkOptions::class.java)
    init {
        targets.registerBinding(Target.Linux::class.java, Target.Linux::class.java)
        targets.registerBinding(Target.MacOs::class.java, Target.MacOs::class.java)
        targets.registerBinding(Target.Windows::class.java, Target.Windows::class.java)
    }

    fun jlink(action: Action<in JlinkOptions>) {
        action.execute(jlink)
    }
}

interface JlinkOptions {
    val modules: ListProperty<String>
}

interface Target : Named {

    val architecture: Property<Architecture>
    val jdkUrl: Property<String>
    interface Linux : Target
    interface MacOs : Target {
        val identifier: Property<String>
        val macIcon: RegularFileProperty
    }

    interface Windows : Target
    enum class Architecture(val arch: String) {
        X86_64("x64"),
        AARCH64("aarch64"),
    }
}
