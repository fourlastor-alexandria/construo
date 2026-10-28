package io.github.fourlastor.construo.task.macos

import io.github.fourlastor.construo.task.BaseTask
import io.github.fourlastor.construo.task.PackageFiles
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermissions
import java.util.stream.Collectors
import javax.inject.Inject

/**
 * Signs a macOS app bundle with rcodesign: ad hoc, or with a certificate (and the hardened
 * runtime), sealing every file of the bundle.
 *
 * A signature seals the files outside `Contents/MacOS` by their digest, and expects only code
 * inside it. roast finds its runtime, its config and the jar next to itself, so they move to
 * `Contents/Resources`, and links in `Contents/MacOS` lead to them.
 */
abstract class SignMacAppBundle @Inject constructor(
    private val execOperations: ExecOperations,
    private val fileSystemOperations: FileSystemOperations,
    private val objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : BaseTask() {

    @get:InputDirectory
    abstract val appDirectory: DirectoryProperty

    @get:Input
    abstract val executableName: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val rcodesign: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val certificate: RegularFileProperty

    // a secret: it isn't recorded with the task's inputs
    @get:Internal
    abstract val certificatePassword: Property<String>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val entitlementsFile: RegularFileProperty

    @get:Input
    abstract val hardenedRuntime: Property<Boolean>

    @get:Input
    abstract val forNotarization: Property<Boolean>

    @get:Internal
    abstract val packageFiles: MapProperty<String, RegularFile>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    fun getPackageFileInputs(): FileCollection = objectFactory.fileCollection().from(packageFiles.map { it.values })

    @Input
    fun getPackageFileMappings(): Map<String, String> =
        PackageFiles.mappings(packageFiles.get(), projectLayout.projectDirectory.asFile)

    @TaskAction
    fun run() {
        check(!OperatingSystem.current().isWindows) { Rcodesign.UNSUPPORTED_HOST }
        check(certificate.isPresent || !forNotarization.get()) {
            "Notarization needs an app signed with a Developer ID Application certificate: set signing.certificate"
        }
        val app = outputDirectory.get().asFile
        fileSystemOperations.delete { delete(app) }
        fileSystemOperations.copy {
            from(appDirectory)
            into(app)
        }
        val macOs = app.resolve("Contents/MacOS")
        addPackageFiles(macOs)
        linkToResources(macOs)

        val workDir = temporaryDir
        fileSystemOperations.delete { delete(*workDir.listFiles().orEmpty()) }
        val log = File(workDir, "rcodesign.log")
        val passwordFile = File(workDir, "certificate-password")
        try {
            val identity = identityArguments(passwordFile)
            if (certificate.isPresent) {
                signJarNatives(app, identity, workDir, log)
            }
            val mainExecutable = File(macOs, executableName.get())
            val args = mutableListOf("sign")
            args += identity
            args += notarizationArguments()
            if (isHardened()) {
                // the flags given without a path are the main executable's only
                args += listOf("--code-signature-flags", "runtime")
                machOFiles(app).filter { it != mainExecutable }.forEach {
                    args += listOf("--code-signature-flags", "${it.relativeTo(app).invariantSeparatorsPath}:runtime")
                }
            }
            entitlements(workDir)?.let { args += listOf("--entitlements-xml-file", it.absolutePath) }
            args += app.absolutePath
            rcodesign(args, log, "sign ${app.name}")
        } finally {
            passwordFile.delete()
        }
    }

    private fun addPackageFiles(macOs: File) {
        packageFiles.get().forEach { (destination, source) ->
            val sourceFile = source.asFile
            require(sourceFile.isFile) { "Package file '$sourceFile' does not exist or is not a regular file" }
            val target = File(macOs, PackageFiles.normalize("Package file destination", destination))
            check(!target.exists()) { "Duplicate package entry: Contents/MacOS/$destination" }
            sourceFile.copyTo(target)
        }
    }

    private fun linkToResources(macOs: File) {
        val resources = File(macOs.parentFile, "Resources")
        macOs.listFiles()!!
            .filter { it.name != executableName.get() }
            .sortedBy { it.name }
            .forEach { file ->
                val moved = File(resources, file.name)
                check(!moved.exists()) { "Can't move Contents/MacOS/${file.name} to Contents/Resources: it's already there" }
                resources.mkdirs()
                Files.move(file.toPath(), moved.toPath())
                Files.createSymbolicLink(file.toPath(), Paths.get("..", "Resources", file.name))
            }
    }

    private fun identityArguments(passwordFile: File): List<String> {
        if (!certificate.isPresent) return emptyList()
        writeSecret(passwordFile, certificatePassword.getOrElse(""))
        return listOf(
            "--p12-file",
            certificate.get().asFile.absolutePath,
            "--p12-password-file",
            passwordFile.absolutePath,
        )
    }

    // rcodesign checks it's a Developer ID certificate and has a time-stamp, which the notary service asks for
    private fun notarizationArguments() = if (forNotarization.get()) listOf("--for-notarization") else emptyList()

    // notarization asks for the hardened runtime, and rcodesign turns it on for it anyway
    private fun isHardened() = hardenedRuntime.get() || forNotarization.get()

    private fun signJarNatives(app: File, identity: List<String>, workDir: File, log: File) {
        val jars = regularFiles(app).filter { it.name.endsWith(".jar") }
        jars.forEachIndexed { index, jar ->
            when (val scan = JarNatives.scan(jar)) {
                is JarNatives.Scan.None -> Unit
                is JarNatives.Scan.SignedJar -> logger.warn(
                    "${jar.relativeTo(app)} is a signed jar with native libraries for macOS: they're left as they are, " +
                        "and notarization may refuse them"
                )
                is JarNatives.Scan.Natives -> JarNatives.sign(jar, scan.entries, File(workDir, "jar-$index")) { native ->
                    val args = mutableListOf("sign")
                    args += identity
                    args += notarizationArguments()
                    if (isHardened()) {
                        args += listOf("--code-signature-flags", "runtime")
                    }
                    args += native.absolutePath
                    rcodesign(args, log, "sign ${native.name} in ${jar.name}")
                }
            }
        }
    }

    private fun entitlements(workDir: File): File? = when {
        entitlementsFile.isPresent -> entitlementsFile.get().asFile
        isHardened() -> File(workDir, "entitlements.plist").apply { writeText(JVM_ENTITLEMENTS) }
        else -> null
    }

    private fun rcodesign(args: List<String>, log: File, action: String) {
        val result = FileOutputStream(log, true).use { output ->
            execOperations.runRcodesign(rcodesign.get().asFile, temporaryDir, args, output)
        }
        if (result.exitValue != 0) {
            val tail = log.readLines().takeLast(LOG_TAIL)
            val hint = if (tail.any { it.contains("incorrect password given when decrypting PFX data") }) {
                "\nIf the password is right, rcodesign can't read how the .p12 file is encrypted: export it again " +
                    "with the legacy encryption (openssl pkcs12 -export -legacy)"
            } else {
                ""
            }
            throw GradleException(
                "rcodesign couldn't $action:\n${tail.joinToString("\n")}$hint\nFull log: ${log.absolutePath}"
            )
        }
    }

    private fun writeSecret(file: File, secret: String) {
        file.delete()
        val path = file.toPath()
        if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        }
        file.writeText(secret)
    }

    private fun regularFiles(root: File): List<File> = Files.walk(root.toPath()).use { paths ->
        paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
            .sorted()
            .map(Path::toFile)
            .collect(Collectors.toList())
    }

    private fun machOFiles(root: File): List<File> = regularFiles(root).filter(MachO::isMachO)

    companion object {
        private const val LOG_TAIL = 20

        // what the JVM needs under the hardened runtime: its JIT writes code, and libraries
        // unpacked from jars (LWJGL's, for example) aren't signed by the app's team
        private val JVM_ENTITLEMENTS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>com.apple.security.cs.allow-jit</key>
                <true/>
                <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
                <true/>
                <key>com.apple.security.cs.disable-library-validation</key>
                <true/>
            </dict>
            </plist>
        """.trimIndent() + "\n"
    }
}
