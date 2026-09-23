package dev.lain.claudejb.frontend.jcef

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageMinifierTest {

    @Test
    fun `the shell loses its comments and keeps the four injection markers`() {
        val shell = "<head>\n  <!-- why the CSP\n spans lines -->\n  <!--CSP-->\n  <!--CSS-->\n</head>\n" +
            "<body>\n  <!-- a note -->\n  <!--LIBS-->\n  <!--APP-->\n</body>"

        val out = PageMinifier.shell(shell)

        listOf("<!--CSP-->", "<!--CSS-->", "<!--LIBS-->", "<!--APP-->").forEach { assertTrue(out.contains(it), it) }
        assertFalse(out.contains("why the CSP"))
        assertFalse(out.contains("a note"))
        assertFalse(out.lines().any { it.isBlank() })
    }

    @Test
    fun `css loses comments and indentation but never the text of a string`() {
        val css = "/* header */\n.a {\n    content: \"/* kept */\";\n    margin: 0 auto; /* trailing */\n}\n\n"

        assertEquals(".a {\ncontent: \"/* kept */\";\nmargin: 0 auto;\n}", PageMinifier.css(css))
    }

    @Test
    fun `an escaped quote does not end a css string`() {
        val css = ".a { content: \"x\\\" /* kept */\"; }"

        assertEquals(css, PageMinifier.css(css))
    }

    @Test
    fun `each app script is isolated so one failure does not stop the rest`() {
        val isolated = PageMinifier.isolated("(function(){ boom(); })();")

        assertTrue(isolated.startsWith("try{\n(function(){ boom(); })();\n}catch(e){"))
        assertTrue(isolated.contains("setTimeout(function(){throw e})"))
    }
}
