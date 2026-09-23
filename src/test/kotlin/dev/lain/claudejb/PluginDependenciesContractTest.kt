package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PluginDependenciesContractTest {

    @Test
    fun `the scan reads the plugin descriptor and the modules it declares`() {
        assertTrue(SourceLayout.pluginDescriptor() in SourceLayout.descriptors()) { "plugin.xml is not among ${SourceLayout.descriptors()}" }
        assertTrue(PluginModules.content().isNotEmpty()) { "plugin.xml declares no <content>; the scan would pass vacuously" }
        assertTrue(PluginModules.alwaysLoaded().sumOf { PluginModules.pluginDependencies(it).size } > 0) {
            "no <plugin id> dependency found in the always-loaded descriptors; the pattern has stopped matching"
        }
    }

    @Test
    fun `no descriptor declares a v1 depends, which the modular model does not honour`() {
        val v1 = SourceLayout.descriptors().filter { DEPENDS.containsMatchIn(it.readText()) }
        assertEquals(emptyList<File>(), v1) {
            "A plugin with <content> modules declares its dependencies in <dependencies>; a <depends> next to them is " +
                "either ignored or refused, and either way the dependency it names is no longer checked."
        }
    }

    @Test
    fun `every mandatory dependency is a platform module that every IntelliJ-based IDE ships`() {
        val plugins = PluginModules.alwaysLoaded().flatMap { PluginModules.pluginDependencies(it) }
        assertEquals(
            emptyList<String>(),
            plugins - MODULES_IN_EVERY_IDE,
            "A plugin dependency of plugin.xml or of a required module the target IDE cannot satisfy means the plugin " +
                "does not load at all, and the verifier does not fail the build over missing dependencies. Move it " +
                "into an optional content module, or add it here with the proof that every IDE in the verified range ships it.",
        )
        val ours = PluginModules.content().keys
        val modules = PluginModules.alwaysLoaded().flatMap { PluginModules.moduleDependencies(it) }
            .filterNot { it in ours || it.startsWith(PLATFORM_MODULE) }
        assertEquals(emptyList<String>(), modules) {
            "A required module depends on a module that is neither this plugin's nor the platform's own ($PLATFORM_MODULE*)."
        }
    }

    @Test
    fun `every content module has a descriptor that exists`() {
        val missing = PluginModules.content().keys.filter { PluginModules.descriptorOf(it) == null }
        assertEquals(emptyList<String>(), missing) {
            "plugin.xml's <content> names a module with no <name>.xml in any module's resources; the platform refuses the plugin"
        }
    }

    @Test
    fun `the verifier is not asked to fail on missing dependencies, because it fails the optional ones too`() {
        val failureLevels = SourceLayout.buildScripts()
            .firstNotNullOfOrNull { Regex("""failureLevel\s*=\s*listOf\(([^)]*)\)""").find(it.readText()) }
        assertTrue(failureLevels != null, "No failureLevel list found in ${SourceLayout.buildScripts()}")
        assertFalse(
            "MISSING_DEPENDENCIES" in failureLevels!!.groupValues[1],
            "FailureLevel.MISSING_DEPENDENCIES turns every optional dependency a target IDE lacks into a failed " +
                "verification: PyCharm has no com.intellij.modules.java. The mandatory case is guarded by this test.",
        )
    }

    private companion object {
        const val PLATFORM_MODULE = "intellij.platform."
        val DEPENDS = Regex("""<depends\b""")
        val MODULES_IN_EVERY_IDE = setOf("com.intellij.modules.platform", "com.intellij.modules.jcef")
    }
}
