package dev.lain.claudejb.controller.mcp.tools.code

internal object ProblemsViewApi {

    const val MISSING = "this IDE build does not expose the Problems view to plugins, so its problems cannot be read here"

    val available: Boolean by lazy {
        CLASSES.all { name -> runCatching { Class.forName(name, false, ProblemsViewApi::class.java.classLoader) }.isSuccess }
    }

    private val CLASSES = listOf(
        "com.intellij.analysis.problemsView.ProblemsCollector",
        "com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils",
    )
}
