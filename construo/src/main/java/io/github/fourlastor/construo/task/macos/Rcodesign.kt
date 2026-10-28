package io.github.fourlastor.construo.task.macos

import org.apache.commons.io.output.CloseShieldOutputStream
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import java.io.File
import java.io.OutputStream

/**
 * rcodesign (https://github.com/indygreg/apple-platform-rs), which signs and notarizes macOS apps
 * from any host: where its releases are, and how it's run.
 */
internal object Rcodesign {
    const val DEFAULT_VERSION = "0.29.0"
    const val DEFAULT_BASE_URL = "https://github.com/indygreg/apple-platform-rs/releases/download"

    // a signed bundle links files with symbolic links, which Windows makes only with special rights
    const val UNSUPPORTED_HOST = "Signing macOS apps needs a Linux or macOS host"

    private val DEFAULT_SHA256 = mapOf(
        "aarch64-apple-darwin" to "d1a532150adaf90048260d76359261aa716abafc45c53c5dc18845029184334a",
        "aarch64-unknown-linux-musl" to "4af92c87ddf52f5f2d1258a3b4e56c7dcb8f1b2468df744976c5f139e031961f",
        "x86_64-apple-darwin" to "14ef11bedd51a8d95eafd767939ae96d5900e5a61511bef75bb21db6e7c74140",
        "x86_64-unknown-linux-musl" to "dbe85cedd8ee4217b64e9a0e4c2aef92ab8bcaaa41f20bde99781ff02e600002",
    )

    /** The build of rcodesign that runs on a host, as its release names it. */
    fun hostTarget(
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch"),
    ): String {
        val os = when {
            osName.startsWith("Linux") -> "unknown-linux-musl"
            osName.startsWith("Mac") -> "apple-darwin"
            else -> throw IllegalArgumentException(UNSUPPORTED_HOST)
        }
        val arch = when (osArch) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> throw IllegalArgumentException(
                "There's no rcodesign build for $osName ($osArch): set construo.rcodesign.executable to one"
            )
        }
        return "$arch-$os"
    }

    fun archiveName(version: String, target: String) = "apple-codesign-$version-$target.tar.gz"

    fun url(baseUrl: String, version: String, target: String) =
        "${baseUrl.trimEnd('/')}/apple-codesign%2F$version/${archiveName(version, target)}"

    fun defaultSha256(version: String, target: String): String? =
        if (version == DEFAULT_VERSION) DEFAULT_SHA256[target] else null
}

/**
 * Runs rcodesign with [args], writing what it prints to [output] (Gradle's own output when null),
 * and returns its result whatever its exit value.
 */
internal fun ExecOperations.runRcodesign(
    executable: File,
    workingDir: File,
    args: List<String>,
    output: OutputStream? = null,
): ExecResult = exec {
    commandLine(listOf(executable.absolutePath) + args)
    workingDir(workingDir)
    // rcodesign reads every RCODESIGN_ variable as one of its own settings
    environment.keys.removeAll { it.startsWith("RCODESIGN_") }
    if (output != null) {
        // Gradle closes each one when its output ends, maybe while the other one still has some to write
        standardOutput = CloseShieldOutputStream.wrap(output)
        errorOutput = CloseShieldOutputStream.wrap(output)
    }
    isIgnoreExitValue = true
}
