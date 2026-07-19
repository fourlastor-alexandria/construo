package io.github.fourlastor.construo.task

import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class DownloadTask: BaseTask() {

    @get:Input
    abstract val src: Property<String>

    @get:OutputFile
    abstract val dest: RegularFileProperty

    @TaskAction
    fun run() {
        val client = OkHttpClient()
        val destination = dest.get().asFile
        val partial = destination.resolveSibling("${destination.name}.part")
        partial.delete()
        try {
            client.newCall(Request.Builder().url(src.get()).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RuntimeException("${response.request.url} returned failure code ${response.code}")
                }
                requireNotNull(response.body).use {
                    FileUtils.openOutputStream(partial).use { output ->
                        IOUtils.copy(it.byteStream(), output)
                    }
                }
            }
            try {
                Files.move(
                    partial.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    partial.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (failure: Throwable) {
            partial.delete()
            throw TaskExecutionException(this, failure)
        }
    }
}
