@file:Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("../libs.versions.toml")) } }
}

includeBuild("../../badass-runtime-plugin") {
    dependencySubstitution {
        substitute(module("org.beryx:badass-runtime-plugin")).using(project(":"))
    }
}
