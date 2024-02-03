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
    val links: PackageLink
)

@JsonClass(generateAdapter = true)
data class PackageLink(
    @Json(name = "pkg_info_uri")
    val packageInfoUri: String
)
