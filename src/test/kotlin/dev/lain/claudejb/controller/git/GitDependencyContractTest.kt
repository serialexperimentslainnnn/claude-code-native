package dev.lain.claudejb.controller.git

import dev.lain.claudejb.PluginModules
import dev.lain.claudejb.SourceLayout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GitDependencyContractTest {

    private val declaring: List<File> = PluginModules.declaring(PLUGIN)

    @Test
    fun `a descriptor declares Git4Idea whenever the sources use it`() {
        val usesGit = gitPackageSources().any { "git4idea" in it.readText() }
        if (!usesGit) return
        assertTrue(
            declaring.isNotEmpty(),
            "The sources import git4idea, so a content module MUST declare <plugin id=\"$PLUGIN\"/> in its <dependencies> — " +
                "the Gradle bundledPlugins() only covers compilation, not the runtime classloader.",
        )
    }

    @Test
    fun `the Git dependency is optional, never hard`() {
        assertTrue(declaring.isNotEmpty(), "No descriptor declares <plugin id=\"$PLUGIN\"/>")
        val content = PluginModules.content()
        declaring.forEach { descriptor ->
            val entry = content[PluginModules.moduleName(descriptor)]
            assertTrue(
                entry != null && PluginModules.isOptional(entry),
                "Git4Idea must be the dependency of an OPTIONAL content module: an IDE with Git disabled, or a project that " +
                    "is not a working copy, must still load this plugin and run every chat. Declared by ${descriptor.path}, " +
                    "loaded as: $entry",
            )
        }
    }

    @Test
    fun `the build declares the bundled Git plugin, or nothing compiles against it`() {
        assertTrue(
            SourceLayout.buildScripts().any { script ->
                BUNDLED.findAll(script.readText()).any { "\"$PLUGIN\"" in it.groupValues[1] }
            },
            "A build.gradle.kts must declare \"$PLUGIN\" in bundledPlugins() of the intellijPlatform dependencies.",
        )
    }

    private fun gitPackageSources(): List<File> =
        SourceLayout.kotlinFiles().filter { "${SourceLayout.packagePath(it)}/".startsWith("controller/git/") }

    private companion object {
        const val PLUGIN = "Git4Idea"
        val BUNDLED = Regex("""bundledPlugins?\(([^)]*)\)""")
    }
}
