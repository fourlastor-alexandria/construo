package io.github.fourlastor.construo.foojay

import com.squareup.moshi.Moshi
import io.github.fourlastor.construo.Target
import io.github.fourlastor.construo.ToolchainOptions
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.io.IOException

class FooJayClient {

    private val okHttpClient = OkHttpClient()
    private val moshi = Moshi.Builder().build()

    fun getPackageInfo(
        it: ToolchainOptions,
        target: Target
    ): PackageInfo {
        val jvmPackage = okHttpClient.newCall(
            Request.Builder()
                .header("Accept", "application/json")
                .url(
                    HttpUrl.Builder()
                        .scheme("https")
                        .host("api.foojay.io")
                        .encodedPath("/disco/v3.0/packages")
                        .addQueryParameter(it.version.versionParam, it.version.versionString)
                        .addQueryParameter("architecture", target.architecture.get().arch)
                        .addQueryParameter("archive_type", "zip")
                        .addQueryParameter("archive_type", "tar.gz")
                        .addQueryParameter("package_type", "jre")
                        .addQueryParameter("distribution", it.vendor.fooJayAlias())
                        .addQueryParameter("operating_system", target.osName())
                        .addQueryParameter("directly_downloadable", "true")
                        .build()
                )
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to get packages")
            val body = requireNotNull(response.body) { "FooJay packages response was null" }
            requireNotNull(
                moshi.adapter(PackagesResults::class.java)
                    .fromJson(body.source())
            ) { "Packages deserialization returned null" }
                .result
                .first()
        }

        val packageInfo = okHttpClient.newCall(
            Request.Builder()
                .url(jvmPackage.links.packageInfoUri)
                .build()
        )
            .execute()
            .use { response ->
                if (!response.isSuccessful) throw IOException("Failed to get package download")
                val body = requireNotNull(response.body) { "FooJay package download response was null" }
                requireNotNull(
                    moshi.adapter(PackageInfoResults::class.java)
                        .fromJson(body.source())
                ) { "Package download deserialization returned null" }
                    .result
                    .first()
            }
        return packageInfo
    }

    @Suppress("DEPRECATION")
    private fun JvmVendorSpec.fooJayAlias() = when (this) {
        JvmVendorSpec.ADOPTIUM -> "Temurin"
        JvmVendorSpec.ADOPTOPENJDK -> "AOJ"
        JvmVendorSpec.AMAZON -> "Corret->"
        JvmVendorSpec.AZUL -> "Zulu"
        JvmVendorSpec.BELLSOFT -> "Liberica"
        JvmVendorSpec.IBM -> "Semeru"
        JvmVendorSpec.IBM_SEMERU -> "Semeru"
        JvmVendorSpec.ORACLE -> "Oracle OpenJDK"
        JvmVendorSpec.SAP -> "SAP Machine"
        else -> error("Unsupported vendor $this")
    }

    private fun Target.osName() = when (this) {
        is Target.Linux -> "linux"
        is Target.Windows -> "windows"
        is Target.MacOs -> "macos"
        else -> error("Invalid target")
    }
}
