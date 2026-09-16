pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "AirGesture"
include(":app")

// Optional isolated test APK. Never part of the distributable app.
if (providers.gradleProperty("withMicProbe").orNull == "true") include(":micprobe")
