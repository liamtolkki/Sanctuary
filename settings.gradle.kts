rootProject.name = "Sanctuary"

val extendedUiProject = file("../ExtendedUI")
if (extendedUiProject.resolve("settings.gradle.kts").isFile) {
    includeBuild(extendedUiProject)
}
