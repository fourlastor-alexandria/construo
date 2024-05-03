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
        toolchainOptions: ToolchainOptions,
        target: Target
    ): PackageInfo {
        val distribution = getDistribution(toolchainOptions.vendor)
        val jvmPackage = fetchJvmPackage(toolchainOptions, target, distribution)
        return fetchPackageDownloadInfo(jvmPackage)
    }

    private fun getDistribution(
        vendor: JvmVendorSpec,
    ): DistributionInfo = okHttpClient.newCall(
        Request.Builder()
            .header("Accept", "application/json")
            .url(
                HttpUrl.Builder()
                    .scheme("https")
                    .host("api.foojay.io")
                    .encodedPath("/disco/v3.0/distributions")
                    .addQueryParameter("include_versions", "false")
                    .build()
            )
            .build()

    )
        .execute()
        .use { response ->
            if (!response.isSuccessful) throw IOException("Failed to get distributions")
            val body = requireNotNull(response.body) { "FooJay distributions response was null" }
            requireNotNull(
                moshi.adapter(DistributionResults::class.java)
                    .fromJson(body.source())
            ) { "Distribution deserialization returned null" }
        }
        .result
        .asSequence()
        .filter {
            if (!it.buildOfOpenjdk) return@filter false
            val alias = vendor.fooJayAlias()
            if (alias != null) {
                return@filter it.name == alias
            }

            vendor.matches(it.name) || it.synonyms.any { vendor.matches(it) }
        }
        .firstOrNull()
        .let { requireNotNull(it) { "Cannot find distribution for $vendor" } }

    private fun fetchJvmPackage(
        toolchainOptions: ToolchainOptions,
        target: Target,
        distribution: DistributionInfo,
    ) = okHttpClient.newCall(
        Request.Builder()
            .header("Accept", "application/json")
            .url(
                HttpUrl.Builder()
                    .scheme("https")
                    .host("api.foojay.io")
                    .encodedPath("/disco/v3.0/packages")
                    .addQueryParameter(toolchainOptions.version.versionParam, toolchainOptions.version.versionString)
                    .addQueryParameter("architecture", target.architecture.get().arch)
                    .addQueryParameter("archive_type", "zip")
                    .addQueryParameter("archive_type", "tar.gz")
                    .addQueryParameter("archive_type", "tgz")
                    .addQueryParameter("distribution", distribution.apiParameter)
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
            .let { packages -> packages.first { it.packageType == "jdk" } }
    }

    private fun fetchPackageDownloadInfo(jvmPackage: Package) =
        okHttpClient.newCall(
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

    @Suppress("DEPRECATION")
    private fun JvmVendorSpec.fooJayAlias() = when (this) {
        JvmVendorSpec.ADOPTIUM -> "Temurin"
        JvmVendorSpec.ADOPTOPENJDK -> "AOJ"
        JvmVendorSpec.AMAZON -> "Corretto"
        JvmVendorSpec.AZUL -> "Zulu"
        JvmVendorSpec.BELLSOFT -> "Liberica"
        JvmVendorSpec.IBM -> "Semeru"
        JvmVendorSpec.IBM_SEMERU -> "Semeru"
        JvmVendorSpec.ORACLE -> "Oracle OpenJDK"
        JvmVendorSpec.SAP -> "SAP Machine"
        else -> null
    }

    private fun Target.osName() = when (this) {
        is Target.Linux -> "linux"
        is Target.Windows -> "windows"
        is Target.MacOs -> "macos"
        else -> error("Invalid target")
    }
}
