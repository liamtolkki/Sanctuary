import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "dev.liamtolkkinen"
version = providers.gradleProperty("releaseVersion")
    .orElse("0.1.0-SNAPSHOT")
    .get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.xenondevs.xyz/releases")
}

val extendedItemsVersion = "0.1.0-alpha.2"
val extendedItemsJar = layout.buildDirectory.file(
    "dependencies/extendeditems-$extendedItemsVersion.jar"
)

val downloadExtendedItems by tasks.registering {
    group = "build setup"
    description = "Downloads the pinned ExtendedItems GitHub Release JAR."
    outputs.file(extendedItemsJar)

    doLast {
        val outputPath = extendedItemsJar.get().asFile.toPath()
        Files.createDirectories(outputPath.parent)

        val temporaryPath = outputPath.resolveSibling("${outputPath.fileName}.download")
        val releaseUrl = URI.create(
            "https://github.com/liamtolkki/ExtendedItems/releases/download/" +
                "v$extendedItemsVersion/extendeditems-$extendedItemsVersion.jar"
        )

        try {
            val client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
            val request = HttpRequest.newBuilder(releaseUrl)
                .header("User-Agent", "Sanctuary-Gradle-Build")
                .GET()
                .build()
            val response = client.send(
                request,
                HttpResponse.BodyHandlers.ofFile(temporaryPath)
            )

            if (response.statusCode() !in 200..299) {
                throw GradleException(
                    "Failed to download ExtendedItems $extendedItemsVersion from " +
                        "$releaseUrl: HTTP ${response.statusCode()}"
                )
            }

            Files.move(
                temporaryPath,
                outputPath,
                StandardCopyOption.REPLACE_EXISTING
            )
        } finally {
            Files.deleteIfExists(temporaryPath)
        }
    }
}

dependencies {
    // ExtendedUI is still resolved from its sibling composite build during development.
    implementation("dev.liamtolkkinen:ExtendedUI:0.1.0-SNAPSHOT")

    // ExtendedItems is pinned to the authoritative release containing Sanctuary IDs.
    implementation(files(extendedItemsJar))

    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    testImplementation("io.papermc.paper:paper-api:26.1.2.build.+")

    implementation("org.xerial:sqlite-jdbc:3.53.2.1") {
        exclude(group = "org.slf4j")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(downloadExtendedItems)
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("sanctuary")
    archiveClassifier.set("plain")
}

tasks.named<ShadowJar>("shadowJar") {
    dependsOn(downloadExtendedItems)
    archiveBaseName.set("sanctuary")
    archiveClassifier.set("")

    mergeServiceFiles()

    relocate(
        "dev.liamtolkkinen.extendedui",
        "dev.liamtolkkinen.sanctuary.lib.extendedui"
    )
    relocate(
        "dev.liamtolkkinen.extendeditems",
        "dev.liamtolkkinen.sanctuary.lib.extendeditems"
    )
    relocate(
        "xyz.xenondevs.invui",
        "dev.liamtolkkinen.sanctuary.lib.invui"
    )
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

val devServerPluginsDir = providers.gradleProperty("devServerPluginsDir")
    .orElse("C:/MinecraftDev/server/plugins")

val deployDev by tasks.registering(Copy::class) {
    group = "development"
    description = "Builds and copies the shaded Sanctuary plugin to the development Paper server."
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(devServerPluginsDir)
}

tasks.wrapper {
    gradleVersion = "9.7.1"
    distributionType = Wrapper.DistributionType.BIN
}
