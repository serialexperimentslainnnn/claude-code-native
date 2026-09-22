package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.navigation.ChooseByNameContributorEx
import com.intellij.navigation.NavigationItem
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FindSymbolParameters

internal fun interface SymbolSource {

    fun symbolsInto(query: String, max: Int, into: MutableSet<NavigationItem>)
}

internal class ContributorSymbolSource(
    private val contributor: ChooseByNameContributorEx,
    private val scope: GlobalSearchScope,
    private val parameters: FindSymbolParameters,
) : SymbolSource {

    override fun symbolsInto(query: String, max: Int, into: MutableSet<NavigationItem>) {
        for (name in namesMatching(query, max)) {
            if (into.size >= max) break
            contributor.processElementsWithName(name, { item -> gather(item, into, max) }, parameters)
        }
    }

    private fun namesMatching(query: String, max: Int): Set<String> {
        val names = LinkedHashSet<String>()
        contributor.processNames({ name -> keep(name, query, names, max) }, scope, null)
        return names
    }

    private fun keep(name: String, query: String, names: MutableSet<String>, max: Int): Boolean {
        if (name.contains(query, ignoreCase = true)) names += name
        return names.size < max
    }

    private fun gather(item: NavigationItem, into: MutableSet<NavigationItem>, max: Int): Boolean {
        into += item
        return into.size < max
    }
}

internal object SymbolSearch {

    data class Outcome(val items: List<NavigationItem>, val sources: Int, val refused: Int) {

        val nothingAnswered: Boolean = refused > 0 && items.isEmpty()

        val partial: Boolean = refused > 0 && items.isNotEmpty()
    }

    fun collect(sources: List<SymbolSource>, query: String, max: Int): Outcome {
        val items = LinkedHashSet<NavigationItem>()
        var refused = 0
        for (source in sources) {
            if (items.size >= max) break
            if (!answered(source, query, max, items)) refused++
        }
        return Outcome(items.toList(), sources.size, refused)
    }

    private fun answered(source: SymbolSource, query: String, max: Int, into: MutableSet<NavigationItem>): Boolean =
        try {
            source.symbolsInto(query, max, into)
            true
        } catch (_: UnsupportedOperationException) {
            false
        }
}
