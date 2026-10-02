package dev.lain.claudejb.frontend.jcef

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

internal fun edtNow(block: () -> Unit) {
    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread) block() else app.invokeLater(block, ModalityState.any())
}
