plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.thitbokobe.host"
version = "1.0.0"

// Repositories are declared centrally in settings.gradle.kts
// (dependencyResolutionManagement), since repositoriesMode is set to
// FAIL_ON_PROJECT_REPOS there. Declaring a `repositories { ... }` block
// here in the project build file is not allowed and fails the build with:
//   "Build was configured to prefer settings repositories over project
//    repositories but repository 'MavenLocal' was added by build file..."

dependencies {
    // The JVM artifact of CloudStream's `library` module, published via
    // `./gradlew :library:publishToMavenLocal` from the cloudstream-master
    // checkout. Group/version come straight from library/build.gradle.kts.
    implementation("com.lagradost.api:library-jvm:1.0.1")

    // Everything below is already a transitive dependency of `library`,
    // listed explicitly so version resolution stays predictable.
    implementation("com.github.Blatzar:NiceHttp:0.4.18")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.thitbokobe.host.MainKt")
}

tasks.shadowJar {
    archiveBaseName.set("cs3-desktop-host")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
