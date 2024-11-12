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
    abstract val jarTask: Property<String>
    val targets: ExtensiblePolymorphicDomainObjectContainer<Target> = objectFactory.polymorphicDomainObjectContainer(Target::class.java)
    val jlink: JlinkOptions = objectFactory.newInstance(JlinkOptions::class.java).apply {
        guessModulesFromJar.convention(true)
        includeDefaultCryptoModules.convention(true)
    }
    val roast: RoastOptions = objectFactory.newInstance(RoastOptions::class.java)

    init {
        targets.registerBinding(Target.Linux::class.java, Target.Linux::class.java)
        targets.registerBinding(Target.MacOs::class.java, Target.MacOs::class.java)
        targets.registerBinding(Target.Windows::class.java, Target.Windows::class.java)
    }

    fun jlink(action: Action<in JlinkOptions>) {
        action.execute(jlink)
    }

    fun roast(action: Action<in RoastOptions>) {
        action.execute(roast)
    }
}

interface JlinkOptions {
    val modules: ListProperty<String>
    val guessModulesFromJar: Property<Boolean>
    val includeDefaultCryptoModules: Property<Boolean>
}

interface RoastOptions {
    val vmArgs: ListProperty<String>
    val useZgc: Property<Boolean>
    val useMainAsContextClassLoader: Property<Boolean>
}

interface Target : Named {

    val architecture: Property<Architecture>
    val jdkUrl: Property<String>
    interface Linux : Target
    interface MacOs : Target {
        val identifier: Property<String>
        val macIcon: RegularFileProperty
    }

    interface Windows : Target {
        val useGpuHint: Property<Boolean>
        val useConsole: Property<Boolean>
    }
    enum class Architecture(val arch: String) {
        X86_64("x64"),
        AARCH64("aarch64"),
    }
}
