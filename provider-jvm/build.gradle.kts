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

    // Already transitive via library-jvm, listed explicitly for clarity
    implementation("com.github.Blatzar:NiceHttp:0.4.18")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

kotlin {
    jvmToolchain(17)
}

tasks.jar {
    archiveBaseName.set("anime47provider")
    // Only this provider's own classes go in the jar — library-jvm and its
    // deps are supplied separately by whatever host loads this jar.
}
