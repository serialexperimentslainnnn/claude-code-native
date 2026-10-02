val sourceSets = the<SourceSetContainer>()

val uiTest =
    sourceSets.create("uiTest") {
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += output + compileClasspath
    }

configurations {
    named("uiTestImplementation") { extendsFrom(configurations["testImplementation"]) }
    named("uiTestRuntimeOnly") { extendsFrom(configurations["testRuntimeOnly"]) }
}

dependencies {
    "uiTestImplementation"(platform("org.junit:junit-bom:6.1.3"))
    "uiTestImplementation"("org.junit.jupiter:junit-jupiter")
    "uiTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "uiTestImplementation"("com.intellij.remoterobot:remote-robot:0.11.23")
    "uiTestImplementation"("com.intellij.remoterobot:remote-fixtures:0.11.23")
    "uiTestImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
}

tasks.register<Test>("checkDrift") {
    description = "Download latest SDK + probe the installed binary; report protocol drift (on-demand)."
    group = "verification"
    useJUnitPlatform {
        includeTags("driftLive")
        includeEngines("junit-jupiter")
    }
    filter { includeTestsMatching("dev.lain.claudejb.drift.*") }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    outputs.upToDateWhen { false }
    val binaryPath =
        providers.gradleProperty("claudeBinary").orNull
            ?: providers.environmentVariable("CLAUDE_BINARY").orNull
            ?: "${System.getProperty("user.home")}/.local/bin/claude"
    systemProperty("claudejb.drift.projectDir", rootDir.absolutePath)
    systemProperty("claudejb.drift.sdkDir", file("node_modules/@anthropic-ai/claude-agent-sdk").absolutePath)
    systemProperty("claudejb.drift.binary", binaryPath)
    systemProperty("claudejb.drift.baseline", file("scripts/drift-baseline.properties").absolutePath)
    testLogging { showStandardStreams = true }
}

tasks.register("integrationTest") {
    description = "Runs only the headless + fake-claude integration tests (subset of `test`)."
    group = "verification"
    finalizedBy(tasks.named("test"))
    doFirst {
        tasks.named<Test>("test").get().filter {
            includeTestsMatching("dev.lain.claudejb.headless.*")
            includeTestsMatching("dev.lain.claudejb.integration.*")
        }
    }
}

tasks.register<Test>("uiTest") {
    description = "End-to-end UI tests driving the IDE via RemoteRobot (Layer D)."
    group = "verification"
    useJUnitPlatform()
    testClassesDirs = uiTest.output.classesDirs
    classpath = uiTest.runtimeClasspath
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
    )
    shouldRunAfter("integrationTest")
    System.getProperty("robot-server.url")?.let { systemProperty("robot-server.url", it) }
    doFirst {
        if (project.findProperty("uiTest.enabled") != "true") {
            throw GradleException(
                "uiTest needs an IDE already running with robot-server on a display, and does not start " +
                    "one. Boot it with `./gradlew runIdeForUiTests` (under xvfb-run if headless), then " +
                    "re-run this task with -PuiTest.enabled=true.",
            )
        }
    }
}

tasks.named<Copy>("processUiTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
