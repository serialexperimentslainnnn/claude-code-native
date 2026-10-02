package dev.lain.claudejb.frontend.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class PageAssemblyTest {

    private val page = PageAssembly.build()

    private val csp = page.headers.getValue("Content-Security-Policy")

    private fun inline(tag: String): List<String> =
        Regex("<$tag>([\\s\\S]*?)</$tag>").findAll(page.html).map { it.groupValues[1] }.toList()

    private fun hash(s: String): String =
        "'sha256-" + Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray(StandardCharsets.UTF_8)),
        ) + "'"

    @Test
    fun `the app ships as one script beside the three vendored libraries`() {
        assertEquals(4, inline("script").size)
    }

    @Test
    fun `every inline script and the stylesheet are admitted by their own hash`() {
        inline("script").forEach { assertTrue(csp.contains(hash(it)), "a script is not in the CSP") }
        inline("style").forEach { assertTrue(csp.contains("style-src ${hash(it)}"), "the stylesheet is not in the CSP") }
    }

    @Test
    fun `the meta policy and the header policy are the same policy`() {
        assertTrue(page.html.contains("content=\"$csp\""))
    }

    @Test
    fun `the page is assembled once for every tab`() {
        assertSame(PageAssembly.page, PageAssembly.page)
    }
}
