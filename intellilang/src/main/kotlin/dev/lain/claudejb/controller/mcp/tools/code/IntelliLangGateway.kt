package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiLanguageInjectionHost
import dev.lain.claudejb.model.mcp.ToolException
import dev.lain.claudejb.util.InstalledPlugins
import org.intellij.plugins.intelliLang.inject.InjectedLanguage
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry

internal class IntelliLangGateway(private val project: Project) : LanguageInjection {

    override suspend fun inject(host: PsiLanguageInjectionHost, languageId: String): Boolean {
        val language = injectable(languageId)
        return writeCommandAction(project, "Claude: inject $languageId") {
            runCatching { TemporaryPlacesRegistry.getInstance(project).addHostWithUndo(host, language) }
                .getOrElse { throw ToolException("IntelliLang refused the injection: ${it.message}", it) }
            true
        }
    }

    private fun injectable(languageId: String): InjectedLanguage {
        if (!InstalledPlugins.isEnabled(PLUGIN_ID)) throw ToolException(LanguageInjection.MISSING)
        return InjectedLanguage.create(languageId) ?: throw ToolException("IntelliLang knows no language with id $languageId")
    }

    private companion object {
        const val PLUGIN_ID = "org.intellij.intelliLang"
    }
}
