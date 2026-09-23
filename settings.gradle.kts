import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "claude-code-native"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.19.0"
    id("org.jetbrains.kotlin.jvm") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("rpc") version "2.4.0-RC-0.1" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
    id("dev.detekt") version "2.0.0-alpha.6" apply false
    id("com.diffplug.spotless") version "8.10.2" apply false
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

include("shared", "frontend", "backend")
