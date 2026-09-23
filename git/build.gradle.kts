dependencies {
    intellijPlatform {
        bundledModules(
            "intellij.platform.backend",
            "intellij.platform.vcs.impl",
            "intellij.platform.vcs.dvcs.impl",
            "intellij.platform.vcs.log",
            "intellij.platform.vcs.log.impl",
        )
        bundledPlugin("Git4Idea")
    }
    implementation(project(":backend"))
}
