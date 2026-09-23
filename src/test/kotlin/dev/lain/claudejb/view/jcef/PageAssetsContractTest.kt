package dev.lain.claudejb.view.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageAssetsContractTest {

    private val assembly: Class<*> = Class.forName(PAGE_ASSEMBLY, true, javaClass.classLoader)

    private val instance: Any = assembly.getField("INSTANCE").get(null)

    private val appNames: List<String> = names("getAppNames")

    private val cssParts: List<String> = names("getCSS_PARTS")

    private fun names(getter: String): List<String> =
        (assembly.getMethod(getter).invoke(instance) as List<*>).map { it.toString() }

    private fun present(resource: String): Boolean = assembly.getResource(resource) != null

    @Test
    fun `the page declares a full set of scripts and stylesheets`() {
        assertTrue(appNames.size >= MIN_SCRIPTS) { "only ${appNames.size} scripts declared" }
        assertTrue(cssParts.size >= MIN_STYLES) { "only ${cssParts.size} stylesheets declared" }
    }

    @Test
    fun `every declared script and stylesheet is on the classpath the page is built from`() {
        val missing = appNames.filterNot { present("/jcef/$it") } +
            cssParts.filterNot { present("/jcef/css/$it") } +
            listOf("shell.html").filterNot { present("/jcef/$it") }
        assertEquals(emptyList<String>(), missing) {
            "A declared asset is not in the resources the jar is built from. A TypeScript module that did not " +
                "emit, or a name that was renamed on one side only, would ship a page missing part of its UI."
        }
    }

    private companion object {
        const val MIN_SCRIPTS = 30
        const val MIN_STYLES = 8
        const val PAGE_ASSEMBLY = "dev.lain.claudejb.frontend.jcef.PageAssembly"
    }
}
