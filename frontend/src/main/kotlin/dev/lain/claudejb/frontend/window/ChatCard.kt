package dev.lain.claudejb.frontend.window

import com.intellij.openapi.Disposable
import javax.swing.JComponent

internal interface ChatCard : Disposable {
    val component: JComponent

    fun focus()

    fun focusTarget(): JComponent?

    fun whenReady(block: () -> Unit)
}
