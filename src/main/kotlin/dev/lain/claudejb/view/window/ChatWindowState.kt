package dev.lain.claudejb.view.window

import java.util.concurrent.CopyOnWriteArrayList

internal class ChatWindowState {

    private val watchers = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var visible: Boolean = false

    fun reveal() = watchers.forEach { it() }

    fun onReveal(watcher: () -> Unit): () -> Unit {
        watchers += watcher
        return { watchers -= watcher }
    }
}
