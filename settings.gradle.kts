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

rootProject.name = "omniguard"

// Core modules
include(":core:model")
include(":core:data")
include(":core:network")

// Feature modules
include(":feature:onboarding")
include(":feature:falldetection")
include(":feature:guidemehome")
include(":feature:sos")
include(":feature:geofencing")

// Applications & Server
include(":backend-server")
include(":app-android")
include(":app-wear")

