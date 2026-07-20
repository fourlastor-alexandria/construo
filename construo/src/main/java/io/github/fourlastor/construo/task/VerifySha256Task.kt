package io.github.fourlastor.construo.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest

abstract class VerifySha256Task : BaseTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archive: RegularFileProperty

    @get:Input
    abstract val expectedSha256: Property<String>

    @TaskAction
    fun run() {
        val expected = expectedSha256.get().lowercase()
        require(SHA256.matches(expected)) {
            "Expected SHA-256 must contain exactly 64 hexadecimal characters"
        }
        val archiveFile = archive.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        archiveFile.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actual != expected) {
            archiveFile.delete()
            error("SHA-256 mismatch for '${archiveFile.name}': expected $expected, got $actual")
        }
    }

    companion object {
        private val SHA256 = Regex("[0-9a-f]{64}")
    }
}
