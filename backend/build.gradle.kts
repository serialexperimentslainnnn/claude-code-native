dependencies {
    intellijPlatform {
        bundledModules(
            "intellij.platform.backend",
            "intellij.platform.kernel.backend",
            "intellij.platform.rpc.backend",
        )
        bundledPlugins(
            "org.jetbrains.plugins.terminal",
            "Git4Idea",
            "org.jetbrains.plugins.github",
            "com.intellij.java",
        )
    }
    implementation(project(":shared"))
}
