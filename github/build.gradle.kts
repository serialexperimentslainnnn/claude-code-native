dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.backend")
        bundledPlugin("org.jetbrains.plugins.github")
    }
    implementation(project(":backend"))
}
