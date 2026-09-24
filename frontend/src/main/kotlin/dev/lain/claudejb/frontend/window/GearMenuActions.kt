package dev.lain.claudejb.frontend.window

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.DumbAware
import dev.lain.claudejb.rpc.GearItem

internal object GearMenuActions {

    fun group(items: List<GearItem>, run: (List<Int>) -> Unit): DefaultActionGroup =
        DefaultActionGroup(items.map { action(it, run) })

    private fun action(item: GearItem, run: (List<Int>) -> Unit): AnAction = when {
        item.separator -> Separator.getInstance()
        item.children.isNotEmpty() -> DefaultActionGroup(item.text, true).apply { addAll(item.children.map { action(it, run) }) }
        else -> Entry(item, run)
    }

    private class Entry(private val item: GearItem, private val run: (List<Int>) -> Unit) :
        AnAction(item.text),
        DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = item.enabled
        }

        override fun actionPerformed(e: AnActionEvent) = run(item.path)
    }
}
