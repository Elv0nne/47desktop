plugins {
    kotlin("jvm") version "2.4.0"
}

group = "com.thitbokobe.provider"
version = "1.0.0"

repositories {
    mavenLocal()
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    // Published from the cloudstream-master checkout via:
    //   ./gradlew :library:publishToMavenLocal
    implementation("com.lagradost.api:library-jvm:1.0.1")

    // NOTE: library-jvm declares these as `implementation` in its own
    // build, which means they do NOT leak out transitively to consumers
    // of the published artifact. They must be listed here explicitly or
    // the compiler will fail with "Unresolved reference" errors.
    implementation("com.github.Blatzar:NiceHttp:0.4.18")
    implementation("org.jsoup:jsoup:1.18.1")
    // NOTE: library-jvm pins this to `strictly 2.13.1` (Android TV/FireStick
    // compatibility requirement from the upstream CloudStream `library`
    // module). `strictly` cannot be overridden by a plain `implementation`
    // version elsewhere in the graph — declaring 2.18.2 here would cause
    // an unresolvable version conflict at build time, the same way it did
    // in cs3-desktop-host/host-app.
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    archiveBaseName.set("anime47provider")
    // Only this provider's own classes go in the jar — library-jvm and its
    // deps are supplied separately by whatever host loads this jar.
}
