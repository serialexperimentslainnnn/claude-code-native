package dev.lain.claudejb.controller.mcp.tools.code

import com.intellij.psi.PsiLanguageInjectionHost

interface LanguageInjection {

    suspend fun inject(host: PsiLanguageInjectionHost, languageId: String): Boolean

    companion object {
        const val MISSING =
            "the IntelliLang plugin (org.intellij.intelliLang) is not installed or is disabled, so nothing injects languages"
    }
}
