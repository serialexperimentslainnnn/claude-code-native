package dev.lain.claudejb.controller.github

import dev.lain.claudejb.MainSources
import dev.lain.claudejb.PluginModules
import dev.lain.claudejb.SourceLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GitHubGatewayContractTest {

    private val sources: List<File> = SourceLayout.kotlinFiles()

    @Test
    fun `the gateway exists and is the only file naming a GitHub plugin type`() {
        val importers = sources
            .filter { file -> MainSources.codeOf(file).any { GITHUB_TYPE.containsMatchIn(it) } }
            .map { it.name }
            .sorted()
        assertEquals(listOf(GATEWAY), importers) {
            "org.jetbrains.plugins.github may be named by $GATEWAY only: with the GitHub plugin disabled those classes are " +
                "not on our classpath, and any other file naming them dies with a LinkageError when first touched."
        }
    }

    @Test
    fun `every entry of the gateway checks the plugin before touching it`() {
        val code = MainSources.codeOf(sources.single { it.name == GATEWAY })
        val entries = code.withIndex().filter { (_, line) -> ENTRY.matches(line) }
        assertTrue(entries.isNotEmpty()) { "$GATEWAY has no public entry point; the pattern has stopped matching" }
        val unguarded = entries.filter { (index, _) -> code[index + 1].trim() != "$GUARD()" }.map { it.value.trim() }
        assertEquals(emptyList<String>(), unguarded) { "every non-private function of $GATEWAY starts with $GUARD()" }
    }

    @Test
    fun `the GitHub plugin is the dependency of an optional content module, and the build compiles against it`() {
        val declaring = PluginModules.declaring(PLUGIN)
        assertTrue(declaring.isNotEmpty(), "No descriptor declares <plugin id=\"$PLUGIN\"/> in its <dependencies>")
        declaring.forEach { descriptor ->
            val entry = PluginModules.content()[PluginModules.moduleName(descriptor)]
            assertTrue(entry != null && PluginModules.isOptional(entry)) {
                "the GitHub dependency must belong to an optional content module named in plugin.xml: ${descriptor.path} is $entry"
            }
        }
        assertTrue(
            SourceLayout.buildScripts().any { script -> BUNDLED.findAll(script.readText()).any { "\"$PLUGIN\"" in it.groupValues[1] } },
        )
    }

    private companion object {
        const val GATEWAY = "GitHubGateway.kt"
        const val GUARD = "requireGitHub"

        val GITHUB_TYPE = Regex("""\borg\.jetbrains\.plugins\.github\.[A-Za-z]""")
        val ENTRY = Regex("""^ {4}(override )?(suspend )?fun \w+\(.*""")
        const val PLUGIN = "org.jetbrains.plugins.github"

        val BUNDLED = Regex("""bundledPlugins?\(([^)]*)\)""")
    }
}
