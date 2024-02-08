package io.github.fourlastor.construo.foojay

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

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
    val links: PackageLink,
    @Json(name = "package_type")
    val packageType: String
)

@JsonClass(generateAdapter = true)
data class PackageLink(
    @Json(name = "pkg_info_uri")
    val packageInfoUri: String
)

@JsonClass(generateAdapter = true)
data class DistributionResults(
    @Json(name = "result")
    val result: List<DistributionInfo>
)
@JsonClass(generateAdapter = true)
data class DistributionInfo(
    @Json(name = "name")
    val name: String,
    @Json(name = "api_parameter")
    val apiParameter: String,
    @Json(name = "build_of_openjdk")
    val buildOfOpenjdk: Boolean,
    @Json(name = "build_of_graalvm")
    val buildOfGraalvm: Boolean,
    @Json(name = "synonyms")
    val synonyms: List<String>,
)
