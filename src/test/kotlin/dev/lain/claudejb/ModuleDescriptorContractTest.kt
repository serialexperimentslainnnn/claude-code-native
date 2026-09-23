package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ModuleDescriptorContractTest {

    @Test
    fun `every module has its descriptor where the platform looks for it`() {
        val missing = SourceLayout.MODULE_NAMES.keys.map { SourceLayout.moduleDescriptor(it) }.filterNot { it.isFile }
        assertEquals(emptyList<File>(), missing) {
            "A content module is described by <module name>.xml at the root of its resources; without it the platform " +
                "refuses the plugin."
        }
    }

    @Test
    fun `the plugin descriptor declares the three modules in its content`() {
        val declared = PluginModules.content()
        assertEquals(emptyList<String>(), SourceLayout.MODULE_NAMES.values.filterNot { it in declared }) {
            "plugin.xml's <content> must name the shared, frontend and backend modules; it names ${declared.keys}"
        }
        listOf("frontend", "backend").forEach { side ->
            val entry = declared.getValue(SourceLayout.MODULE_NAMES.getValue(side))
            assertTrue(Regex("""\brequired-if-available\s*=\s*"intellij\.platform\.$side"""").containsMatchIn(entry)) {
                "the $side module must be required-if-available=\"intellij.platform.$side\": each side loads where its " +
                    "platform half exists, and nowhere else. Found: $entry"
            }
        }
        val shared = declared.getValue(SourceLayout.MODULE_NAMES.getValue("shared"))
        assertTrue(REQUIRED.containsMatchIn(shared)) { "the shared contract must load wherever the plugin does. Found: $shared" }
    }

    @Test
    fun `the frontend and the backend both depend on the shared contract`() {
        listOf("frontend", "backend").map { SourceLayout.moduleDescriptor(it) }.forEach { descriptor ->
            assertTrue(descriptor.isFile && SHARED in PluginModules.moduleDependencies(descriptor)) {
                "${descriptor.path} does not depend on $SHARED, so its classloader " +
                    "cannot see ChatApi or the DTOs it exchanges."
            }
        }
    }

    @Test
    fun `the tool window is registered by the frontend descriptor only`() = registeredOnlyBy("frontend", TOOL_WINDOW)

    @Test
    fun `the remote API provider is registered by the backend descriptor only`() = registeredOnlyBy("backend", REMOTE_API)

    private fun registeredOnlyBy(module: String, extension: Regex) {
        val owner = SourceLayout.moduleDescriptor(module)
        assertTrue(owner.isFile && extension.containsMatchIn(owner.readText())) { "${owner.path} does not register ${extension.pattern}" }
        val elsewhere = SourceLayout.descriptors()
            .filter { it.canonicalFile != owner.canonicalFile && extension.containsMatchIn(it.readText()) }
        assertEquals(emptyList<File>(), elsewhere) {
            "${extension.pattern} is registered outside ${owner.path}; a second registration runs on the wrong side of a " +
                "remote session, or twice on a local one."
        }
    }

    private companion object {
        const val SHARED = "dev.lain.claudejb.shared"
        val REQUIRED = Regex("""\bloading\s*=\s*"required"""")
        val TOOL_WINDOW = Regex("""<toolWindow\b""")
        val REMOTE_API = Regex("""<platform\.rpc\.backend\.remoteApiProvider\b""")
    }
}
