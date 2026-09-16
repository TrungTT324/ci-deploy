pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CI-Deploy"
include(":app")
include(":libs:appupdate")
include(":libs:core")
include(":libs:logcat")
include(":libs:webserver")
include(":libs:cidata")
include(":libs:bluetooth")
include(":appMouse")
include(":appQa")
