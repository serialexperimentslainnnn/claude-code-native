package dev.lain.claudejb.headless

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.db.DbAccess
import dev.lain.claudejb.controller.git.GitAccess
import dev.lain.claudejb.controller.mcp.tools.code.LanguageInjection
import dev.lain.claudejb.controller.mcp.tools.vcs.GitHubAccess

class OptionalModulesHeadlessTest : BasePlatformTestCase() {

    private fun loaded(plugin: String) = PluginManagerCore.isLoaded(PluginId.getId(plugin))

    private val modules: Map<String, () -> Any?>
        get() = mapOf(
            "com.intellij.database" to { project.serviceOrNull<DbAccess>() },
            "Git4Idea" to { ApplicationManager.getApplication().serviceOrNull<GitAccess>() },
            "org.jetbrains.plugins.github" to { project.serviceOrNull<GitHubAccess>() },
        )

    fun `test the IDE under test has the plugins the optional modules need`() {
        assertEquals(modules.keys.toList(), modules.keys.filter(::loaded))
    }

    fun `test the language injection module loads, since injection is part of the platform`() {
        assertNotNull("dev.lain.claudejb.intellilang did not load", project.serviceOrNull<LanguageInjection>())
    }

    fun `test the database module reaches the database plugin's classes, not only its own service`() {
        val db = project.serviceOrNull<DbAccess>() ?: return fail("the database module did not load")
        assertEquals(emptyList<DbAccess.Connection>(), db.connections())
    }

    fun `test every optional module loads its service where the IDE has its plugin`() {
        val silent = modules.filter { (plugin, lookup) -> loaded(plugin) && lookup() == null }.keys
        assertEquals("optional modules that did not load although their plugin is there", emptySet<String>(), silent)
    }
}
