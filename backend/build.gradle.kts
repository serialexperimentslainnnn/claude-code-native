sourceSets.main {
    kotlin.srcDir(rootProject.file("src/main/kotlin"))
    java.srcDir(rootProject.file("src/main/java"))
    resources.srcDir(rootProject.file("src/main/resources"))
    resources.exclude("META-INF/plugin.xml", "META-INF/pluginIcon*.svg")
}

dependencies {
    intellijPlatform {
        bundledModules(
            "intellij.platform.backend",
            "intellij.platform.kernel.backend",
            "intellij.platform.rpc.backend",
        )
        bundledPlugin("Git4Idea")
    }
    implementation(project(":shared"))
}
