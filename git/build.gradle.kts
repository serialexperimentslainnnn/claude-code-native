dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.backend")
        bundledPlugin("Git4Idea")
    }
    implementation(project(":backend"))
}
