dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.backend")
    }
    implementation(project(":backend"))
}
