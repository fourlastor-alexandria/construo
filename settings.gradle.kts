@file:Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("libs.versions.toml")) } }
}

pluginManagement {
    includeBuild("./construo")
}

include(":test")
