package io.github.fourlastor.construo.task.macos

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry

/**
 * The native libraries a jar carries for macOS, which a program unpacks when it runs. Apple's
 * notary service looks into jars, and refuses the ones whose Mach-O files aren't signed.
 */
internal object JarNatives {

    /** Whether [jar] carries Mach-O files, and why they can't be signed when they can't. */
    sealed interface Scan {
        object None : Scan
        object SignedJar : Scan
        data class Natives(val entries: List<String>) : Scan
    }

    fun scan(jar: File): Scan = ZipFile.builder().setFile(jar).get().use { zip ->
        val natives = zip.entries.asSequence()
            .filter { !it.isDirectory && !it.name.endsWith(".class") && zip.canReadEntryData(it) }
            .filter { entry -> zip.getInputStream(entry).use { MachO.isMachO(it.readNBytes(8)) } }
            .map { it.name }
            .toList()
        when {
            natives.isEmpty() -> Scan.None
            // changing an entry of a signed jar breaks its signature
            zip.entries.asSequence().any { isSignatureFile(it.name) } -> Scan.SignedJar
            else -> Scan.Natives(natives)
        }
    }

    /**
     * Has [sign] sign each Mach-O entry of [jar], unpacked in [workDir], then writes [jar] again with
     * the signed entries in their place; the other entries are copied as they are.
     */
    fun sign(jar: File, entries: List<String>, workDir: File, sign: (File) -> Unit) {
        val signed = mutableMapOf<String, File>()
        workDir.mkdirs()
        val rewritten = File(workDir, jar.name)
        ZipFile.builder().setFile(jar).get().use { zip ->
            entries.forEachIndexed { index, name ->
                val native = File(workDir, "$index/${name.substringAfterLast('/')}")
                native.parentFile.mkdirs()
                zip.getInputStream(zip.getEntry(name)).use { input ->
                    native.outputStream().use { input.copyTo(it) }
                }
                sign(native)
                signed[name] = native
            }
            ZipArchiveOutputStream(rewritten).use { output ->
                zip.entriesInPhysicalOrder.asSequence().forEach { entry ->
                    val native = signed[entry.name]
                    if (native == null) {
                        output.addRawArchiveEntry(entry, zip.getRawInputStream(entry))
                    } else {
                        val replacement = ZipArchiveEntry(entry.name)
                        replacement.time = entry.time
                        replacement.method = ZipEntry.DEFLATED
                        if (entry.platform == ZipArchiveEntry.PLATFORM_UNIX) {
                            replacement.unixMode = entry.unixMode
                        }
                        output.putArchiveEntry(replacement)
                        native.inputStream().use { it.copyTo(output) }
                        output.closeArchiveEntry()
                    }
                }
            }
        }
        Files.move(rewritten.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun isSignatureFile(name: String): Boolean {
        val upper = name.uppercase()
        return upper.startsWith("META-INF/") &&
            upper.indexOf('/', "META-INF/".length) < 0 &&
            listOf(".SF", ".RSA", ".DSA", ".EC").any { upper.endsWith(it) }
    }
}
