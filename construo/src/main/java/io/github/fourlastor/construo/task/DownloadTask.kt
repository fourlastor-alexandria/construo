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

abstract class DownloadTask: BaseTask() {

    @get:Input
    abstract val src: Property<String>

    @get:OutputFile
    abstract val dest: RegularFileProperty

    @TaskAction
    fun run() {
        val client = OkHttpClient()
        val result = runCatching { client.newCall(Request.Builder().url(src.get()).build()).execute() }
        result.onSuccess { response ->
            if (!response.isSuccessful) {
                throw TaskExecutionException(this, RuntimeException("${response.request.url} returned failure code ${response.code}"))
            }
            requireNotNull(response.body).use {
                IOUtils.copy(it.byteStream(), FileUtils.openOutputStream(dest.get().asFile))
            }
        }.onFailure {
            throw TaskExecutionException(this, it)
        }
    }
}
