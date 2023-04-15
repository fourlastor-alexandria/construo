@file:Suppress("UnstableApiUsage")

include(":construo")

dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("libs.versions.toml")) } }
}

include("test")
