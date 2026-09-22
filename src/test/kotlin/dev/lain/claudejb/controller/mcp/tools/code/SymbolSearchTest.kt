package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SymbolSearchTest {

    @Test
    fun `a source that refuses the platform call does not take the ones that answer down with it`() {
        val outcome = SymbolSearch.collect(listOf(refusing(), answering("Alpha")), "a", 10)

        assertEquals(listOf("Alpha"), outcome.items.map { it.name })
        assertEquals(1, outcome.refused)
        assertFalse(outcome.refusedEverywhere)
        assertTrue(outcome.partial) { "a list missing a whole contributor must not read as the complete answer" }
    }

    @Test
    fun `when every source refuses, the outcome says so instead of reading as a project without symbols`() {
        val outcome = SymbolSearch.collect(listOf(refusing(), refusing()), "a", 10)

        assertTrue(outcome.items.isEmpty())
        assertTrue(outcome.refusedEverywhere) { "Rider refuses every contributor; an empty list there would be a lie" }
        assertFalse(outcome.partial)
    }

    @Test
    fun `a refusal is not swallowed as an empty contributor, so the count stays honest`() {
        val outcome = SymbolSearch.collect(listOf(answering(), refusing()), "a", 10)

        assertEquals(1, outcome.refused)
        assertEquals(2, outcome.sources)
    }

    @Test
    fun `no further source is asked once max is reached`() {
        val asked = mutableListOf<String>()
        val counting = SymbolSource { query, _, into ->
            asked += query
            into += item("symbol${asked.size}")
        }

        val outcome = SymbolSearch.collect(listOf(counting, counting), "symbol", 1)

        assertEquals(listOf("symbol"), asked)
        assertEquals(1, outcome.items.size)
    }

    private fun refusing(): SymbolSource = SymbolSource { _, _, _ ->
        throw UnsupportedOperationException("Use RdChooseByNameContributor.processNamesLifetimed")
    }

    private fun answering(vararg names: String): SymbolSource = SymbolSource { _, _, into ->
        names.forEach { into += item(it) }
    }

    private fun item(named: String): NavigationItem = object : NavigationItem {

        override fun getName(): String = named

        override fun getPresentation(): ItemPresentation? = null

        override fun navigate(requestFocus: Boolean) = Unit

        override fun canNavigate(): Boolean = false

        override fun canNavigateToSource(): Boolean = false
    }
}
