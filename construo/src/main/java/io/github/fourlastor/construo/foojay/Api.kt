package io.github.fourlastor.construo.foojay

import com.squareup.moshi.Json

data class PackageInfoResults(
    @Json(name = "result")
    val result: List<PackageInfo>
)

data class PackageInfo(
    @Json(name = "filename")
    val filename: String,
    @Json(name = "direct_download_uri")
    val directDownloadUri: String
)

data class PackagesResults(
    @Json(name = "result")
    val result: List<Package>
)

data class Package(
    @Json(name = "filename")
    val filename: String,
    @Json(name = "links")
    val links: PackageLink
)

data class PackageLink(
    @Json(name = "pkg_info_uri")
    val packageInfoUri: String
)
