rootProject.name = "Sanctuary"

val extendedUiProject = file("../ExtendedUI")
if (extendedUiProject.resolve("settings.gradle.kts").isFile) {
    includeBuild(extendedUiProject)
}

val extendedItemsProject = file("../ExtendedItems")
if (extendedItemsProject.resolve("settings.gradle.kts").isFile) {
    includeBuild(extendedItemsProject)
}
