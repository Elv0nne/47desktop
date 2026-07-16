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
    // NOTE: library-jvm pins this to `strictly 2.13.1` (Android TV/FireStick
    // compatibility requirement from the upstream CloudStream `library`
    // module). `strictly` cannot be overridden by a plain `implementation`
    // version elsewhere in the graph — declaring 2.18.2 here caused:
    //   "Cannot find a version of 'com.fasterxml.jackson.module:jackson-module-kotlin'
    //    that satisfies the version constraints"
    // host-app doesn't use jackson-module-kotlin directly, so just match
    // the version library-jvm already pins.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

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
    // Without clearing archiveVersion, the default naming
    // "{archiveBaseName}-{archiveVersion}.jar" produces
    // "cs3-desktop-host-1.0.0.jar", not "cs3-desktop-host.jar" — the exact
    // filename the CI workflow's next "cp" step expects.
    archiveVersion.set("")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
}
