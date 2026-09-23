dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
    implementation(project(":frontend"))
    implementation(project(":shared"))
}
