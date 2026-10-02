dependencies {
    intellijPlatform {
        bundledModules(
            "intellij.platform.backend",
            "intellij.platform.bookmarks",
        )
    }
    implementation(project(":backend"))
}
