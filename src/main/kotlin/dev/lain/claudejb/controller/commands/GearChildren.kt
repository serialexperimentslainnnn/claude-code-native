package dev.lain.claudejb.controller.commands

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project

internal interface GearChildren {
    fun children(project: Project): List<AnAction>
}
