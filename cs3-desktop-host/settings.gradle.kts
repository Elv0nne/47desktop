pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()   // this is where the CloudStream `library` jar lives after step 1
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "cs3-desktop-host"
include(":host-app")
