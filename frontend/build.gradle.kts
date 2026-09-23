val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.frontend")
        bundledPlugin("com.intellij.modules.jcef")
    }
    implementation(project(":shared"))
}

val npmInstall =
    tasks.register<Exec>("npmInstall") {
        workingDir(rootDir)
        inputs.files(rootProject.file("package.json"), rootProject.file("package-lock.json"))
        outputs.file(rootProject.file("node_modules/.package-lock.json"))
        commandLine(npm, "ci")
    }

val compileWeb =
    tasks.register<Exec>("compileWeb") {
        dependsOn(npmInstall)
        workingDir(rootDir)
        inputs.dir("src/main/ts/jcef")
        inputs.file(rootProject.file("tsconfig.json"))
        outputs.dir(layout.buildDirectory.dir("web"))
        doFirst { delete(layout.buildDirectory.dir("web")) }
        commandLine(npm, "run", "build")
    }

tasks.processResources {
    from(compileWeb)
}
