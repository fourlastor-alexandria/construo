@file:Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    versionCatalogs { create("libs") }
}

pluginManagement {
    includeBuild("./construo")
}

include(":test")
