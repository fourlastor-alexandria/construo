package io.github.fourlastor.construo.task.macos

import java.io.File
import java.nio.ByteBuffer

/** Tells Mach-O files, the code a signature covers, from the rest. */
internal object MachO {
    private const val HEADER_SIZE = 8
    private val THIN_MAGICS = setOf(0xfeedface, 0xcefaedfe, 0xfeedfacf, 0xcffaedfe).map { it.toInt() }
    private val FAT_MAGICS = setOf(0xcafebabe, 0xcafebabf).map { it.toInt() }

    fun isMachO(file: File): Boolean =
        file.length() >= HEADER_SIZE && file.inputStream().use { isMachO(it.readNBytes(HEADER_SIZE)) }

    fun isMachO(header: ByteArray): Boolean {
        if (header.size < HEADER_SIZE) return false
        val buffer = ByteBuffer.wrap(header)
        val magic = buffer.getInt(0)
        // a universal binary starts like a class file: where it counts its architectures, a class
        // file has its version, 45 and up
        return magic in THIN_MAGICS || (magic in FAT_MAGICS && buffer.getInt(4) in 1 until 45)
    }
}
