import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    // During development these are resolved from sibling composite builds
    // when ../ExtendedUI and ../ExtendedItems exist.
    implementation("dev.liamtolkkinen:ExtendedUI:0.1.0-SNAPSHOT")
    implementation("dev.liamtolkkinen:ExtendedItems:0.1.0-SNAPSHOT")

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
