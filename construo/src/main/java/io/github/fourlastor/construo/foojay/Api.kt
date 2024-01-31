package io.github.fourlastor.construo.foojay

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.gradle.jvm.toolchain.JvmVendorSpec

@JsonClass(generateAdapter = true)
data class PackageInfoResults(
    @Json(name = "result")
    val result: List<PackageInfo>
)

@JsonClass(generateAdapter = true)
data class PackageInfo(
    @Json(name = "filename")
    val filename: String,
    @Json(name = "direct_download_uri")
    val directDownloadUri: String
)

@JsonClass(generateAdapter = true)
data class PackagesResults(
    @Json(name = "result")
    val result: List<Package>
)

@JsonClass(generateAdapter = true)
data class Package(
    @Json(name = "filename")
    val filename: String,
    @Json(name = "links")
    val links: PackageLink
)

@JsonClass(generateAdapter = true)
data class PackageLink(
    @Json(name = "pkg_info_uri")
    val packageInfoUri: String
)

@Suppress("DEPRECATION")
object FooJayVendorAliases {
    val values = mapOf(
        JvmVendorSpec.ADOPTIUM to "Temurin",
        JvmVendorSpec.ADOPTOPENJDK to "AOJ",
        JvmVendorSpec.AMAZON to "Corretto",
        JvmVendorSpec.AZUL to "Zulu",
        JvmVendorSpec.BELLSOFT to "Liberica",
        JvmVendorSpec.IBM to "Semeru",
        JvmVendorSpec.IBM_SEMERU to "Semeru",
        JvmVendorSpec.ORACLE to "Oracle OpenJDK",
        JvmVendorSpec.SAP to "SAP Machine"
    )
}
