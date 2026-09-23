dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.backend")
        bundledModule("org.intellij.intelliLang")
    }
    implementation(project(":backend"))
}
