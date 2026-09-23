package dev.lain.claudejb.view.jcef

import dev.lain.claudejb.PluginModules
import dev.lain.claudejb.SourceLayout
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class JcefDependencyContractTest {

    @Test
    fun `every module whose sources use JCEF declares the JCEF plugin`() {
        val users = SourceLayout.MODULE_NAMES.keys.filter { module ->
            sourcesOf(module).any { "com.intellij.ui.jcef" in it.readText() }
        }
        assertTrue(users.isNotEmpty()) { "no module uses com.intellij.ui.jcef; the scan is looking at the wrong tree" }
        users.forEach { module ->
            val descriptor = SourceLayout.moduleDescriptor(module)
            assertTrue(
                descriptor.isFile && JCEF in PluginModules.pluginDependencies(descriptor),
                "The $module sources import com.intellij.ui.jcef, so ${descriptor.path} MUST declare <plugin id=\"$JCEF\"/> " +
                    "in its <dependencies> — since 262 those classes come from a bundled plugin and are NOT on an " +
                    "undeclared module's classpath.",
            )
        }
    }

    @Test
    fun `the JCEF dependency is hard, never optional`() {
        val loose = PluginModules.declaring(JCEF).filterNot { it in PluginModules.alwaysLoaded() }
        assertTrue(
            loose.isEmpty(),
            "com.intellij.modules.jcef must be a HARD dependency of plugin.xml or of a required module: there is no " +
                "browser-less mode to fall back to. Declared by an optional module: $loose",
        )
    }

    private fun sourcesOf(module: String): List<File> {
        val roots = if (module == "backend") listOf("backend", "") else listOf(module)
        return SourceLayout.files(roots.flatMap { SourceLayout.rootsOf(it, "kotlin") }, setOf("kt"))
    }

    @Test
    fun `sinceBuild is not lower than the first build that has the JCEF module`() {
        val since = SourceLayout.buildScripts().firstNotNullOfOrNull { sinceBuildIn(it.readText()) }
        assertNotNull(since, "No sinceBuild found in ${SourceLayout.buildScripts()}")
        assertTrue(
            since!!.count { it == '.' } >= 2,
            "sinceBuild=\"$since\" is a branch, not a build. `253` includes 253.28294.334, where " +
                "com.intellij.modules.jcef does not exist and the plugin cannot load. Pin the full number.",
        )
        assertTrue(
            atLeast(since, FIRST_BUILD_WITH_JCEF_MODULE),
            "sinceBuild=\"$since\" is below $FIRST_BUILD_WITH_JCEF_MODULE, the first build that ships " +
                "com.intellij.modules.jcef. Below it the IDE refuses to load this plugin outright.",
        )
    }

    private fun sinceBuildIn(script: String): String? {
        val match = SINCE_BUILD.find(script) ?: return null
        val literal = match.groupValues[1]
        if (literal.isNotEmpty()) return literal
        return Regex("""\bval\s+${match.groupValues[2]}\s*=\s*"([^"]+)"""").find(script)?.groupValues?.get(1)
    }

    private fun atLeast(actual: String, floor: String): Boolean {
        val a = actual.split('.').mapNotNull { it.toIntOrNull() }
        val f = floor.split('.').mapNotNull { it.toIntOrNull() }
        for (i in f.indices) {
            val left = a.getOrNull(i) ?: return false
            if (left != f[i]) return left > f[i]
        }
        return true
    }

    private companion object {
        const val FIRST_BUILD_WITH_JCEF_MODULE = "253.29346.138"
        const val JCEF = "com.intellij.modules.jcef"
        val SINCE_BUILD = Regex("""\bsinceBuild\s*=\s*(?:"([^"]+)"|(\w+))""")
    }
}
