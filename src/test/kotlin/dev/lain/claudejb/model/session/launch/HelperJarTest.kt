package dev.lain.claudejb.model.session.launch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class HelperJarTest {

    @TempDir
    lateinit var dir: Path

    private fun jar(name: String, vararg entries: String): File = File(dir.toFile(), name).apply {
        JarOutputStream(outputStream()).use { out -> entries.forEach { out.putNextEntry(JarEntry(it)); out.closeEntry() } }
    }

    @Test
    fun `the helper runs from the one jar that holds the bridge`() {
        val other = jar("other.jar", "x/Other.class")
        val holder = jar("plugin.jar", McpConfigBuilder.HELPER_CLASS_FILE)
        val loader = URLClassLoader(arrayOf(other.toURI().toURL(), holder.toURI().toURL()), null)

        assertEquals(holder.canonicalPath, SessionLauncher.jarHolding(loader)?.canonicalPath)
    }

    @Test
    fun `no jar holds the bridge, no jar is named`() {
        val loader = URLClassLoader(arrayOf(jar("other.jar", "x/Other.class").toURI().toURL()), null)

        assertNull(SessionLauncher.jarHolding(loader))
    }
}
