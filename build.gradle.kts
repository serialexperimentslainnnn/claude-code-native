import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlinx.kover")
    id("dev.detekt")
    id("com.diffplug.spotless")
}

group = "dev.lain"
version = "6.5.0"

val platformBuild = "262.8665.258"
val serialization = "1.9.0"
val pluginModules = listOf("shared", "frontend", "backend", "git", "github", "java", "intellilang", "terminal")

allprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(25)
            compilerOptions {
                allWarningsAsErrors.set(true)
                jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
            }
        }
    }
    tasks.withType<ProcessResources>().configureEach {
        exclude("**/PROJECTMAP.md")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.intellij.platform.module")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
    apply(plugin = "rpc")
    apply(plugin = "org.jetbrains.kotlinx.kover")
    extensions.configure<BasePluginExtension> {
        archivesName.set("dev.lain.claudejb.$name")
    }
    dependencies {
        "compileOnly"("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:$serialization")
        "compileOnly"("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:$serialization")
    }
}

sourceSets.main {
    kotlin.setSrcDirs(emptyList<Any>())
    java.setSrcDirs(emptyList<Any>())
    resources.setSrcDirs(listOf("src/main/resources"))
    resources.include("META-INF/**")
}

apply(from = "gradle/test-suites.gradle.kts")
apply(from = "gradle/quality.gradle.kts")
apply(from = "gradle/coverage.gradle.kts")
apply(from = "gradle/release-notes.gradle.kts")

dependencies {
    intellijPlatform {
        intellijIdea(platformBuild) {
            useInstaller = false
        }
        pluginModules.forEach { pluginModule(implementation(project(":$it"))) }
        bundledModules(
            "intellij.platform.frontend",
            "intellij.platform.backend",
            "intellij.platform.kernel.backend",
            "intellij.platform.rpc.backend",
        )
        bundledPlugins(
            "com.intellij.modules.jcef",
            "org.jetbrains.plugins.terminal",
            "Git4Idea",
            "org.jetbrains.plugins.github",
            "com.intellij.java",
        )
        testFramework(TestFrameworkType.Platform)
    }
    pluginModules.forEach { testImplementation(project(":$it")) }
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"
val fakeClaude: String = file("bin/fake-claude").absolutePath

tasks {
    val frontendTest = register<Exec>("frontendTest") {
        dependsOn(":frontend:compileWeb")
        inputs.dir("src/test/frontend")
        inputs.dir("frontend/src/main/resources/jcef")
        inputs.dir("frontend/src/main/ts/jcef")
        outputs.dir(layout.buildDirectory.dir("reports/frontend"))
        environment("CI", "true")
        commandLine(npm, "test")
    }
    check { dependsOn(frontendTest) }
    compileTestKotlin {
        friendPaths.from(pluginModules.map { project(":$it").layout.buildDirectory.dir("classes/kotlin/main") })
    }
    processResources {
        from(file("THIRD-PARTY-NOTICES.md")) { into("META-INF") }
        from(file("LICENSE")) { into("META-INF") }
        from(file("LICENSES")) { into("META-INF/licenses") }
    }
    runIde {
        jvmArgs("-Djb.privacy.policy.text=<!--999.999-->", "-Djb.consents.confirmation.enabled=false", "-Dclaudejb.debug=true")
    }
    test {
        useJUnitPlatform { excludeTags("driftLive", "bench") }
        filter { excludeTestsMatching("*Bench") }
        systemProperty("claudejb.fakeClaude", fakeClaude)
    }
}

intellijPlatformTesting.testIde.register("bench") {
    testFrameworks(TestFrameworkType.Platform)
    task {
        group = "verification"
        description = "Runs the benchmarks and keeps their figures in build/bench/results.txt."
        useJUnitPlatform()
        filter {
            includeTestsMatching("*Bench")
            includeTestsMatching("dev.lain.claudejb.bench.*")
        }
        workingDir(layout.projectDirectory)
        systemProperty("claudejb.fakeClaude", fakeClaude)
        testLogging { showStandardStreams = true }
        val results = layout.buildDirectory.file("bench/results.txt")
        outputs.file(results)
        outputs.upToDateWhen { false }
        doFirst { results.get().asFile.delete() }
        doLast {
            val file = results.get().asFile
            logger.lifecycle(if (file.exists()) file.readText() else "No benchmark wrote a result.")
        }
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            plugins { robotServerPlugin() }
            task {
                args(file("src/uiTest/resources/sandbox-project").absolutePath)
                jvmArgs(
                    "-Drobot-server.port=8082",
                    "-Dide.browser.jcef.jsQueryPoolSize=10000",
                    "-Djb.privacy.policy.text=<!--999.999-->",
                    "-Djb.consents.confirmation.enabled=false",
                    "-Dide.show.tips.on.startup.default.value=false",
                    "-Dide.mac.message.dialogs.as.sheets=false",
                    "-Dide.mac.file.chooser.native=false",
                    "-DjbScreenMenuBar.enabled=false",
                    "-Dapple.laf.useScreenMenuBar=false",
                    "-Didea.trust.all.projects=true",
                    "-Dide.show.new.ui.welcome.screen=false",
                    "-Dclaudejb.fakeClaude=$fakeClaude",
                    "-Dclaudejb.fakeFixture=${file("src/test/resources/fixtures/multi_message.jsonl").absolutePath}",
                )
            }
        }
    }
}

intellijPlatform {
    pluginInstallationTarget = SplitModeAware.PluginInstallationTarget.BOTH
    pluginConfiguration {
        ideaVersion {
            sinceBuild = platformBuild
            untilBuild = "263.*"
        }
        changeNotes = provider { extra["changeNotesHtml"] as String }
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    pluginVerification {
        freeArgs = listOf("-mute", "TemplateWordInPluginName")
        externalPrefixes = listOf("org.jetbrains.uast")
        failureLevel =
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
                VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            )
        ides {
            val localIdes =
                (providers.gradleProperty("localIdePath").orNull ?: providers.environmentVariable("LOCAL_IDE_PATH").orNull)
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.map { file(it) }
                    ?.filter { it.exists() }
                    .orEmpty()
            if (localIdes.isNotEmpty() && !providers.environmentVariable("CI").isPresent) {
                localIdes.forEach { local(it) }
            } else {
                create(IntelliJPlatformType.IntellijIdea, platformBuild) {
                    useInstaller = false
                }
                recommended()
                select {
                    types = listOf(IntelliJPlatformType.IntellijIdea, IntelliJPlatformType.PyCharm)
                    channels = listOf(ProductRelease.Channel.EAP, ProductRelease.Channel.RC)
                    sinceBuild = "263"
                    untilBuild = "263.*"
                }
            }
        }
    }
}
