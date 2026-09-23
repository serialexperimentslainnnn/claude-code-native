dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.backend")
        bundledPlugin("org.intellij.intelliLang")
    }
    implementation(project(":backend"))
}
