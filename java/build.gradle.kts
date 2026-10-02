dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.backend")
        bundledPlugin("com.intellij.java")
    }
    implementation(project(":backend"))
}
