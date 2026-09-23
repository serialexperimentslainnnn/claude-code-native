import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

dependencies {
    listOf("shared", "frontend", "backend").forEach { "kover"(project(":$it")) }
}

configure<KoverProjectExtension> {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll("checkDrift", "uiTest", "bench")
        }
        sources {
            excludedSourceSets.add("uiTest")
        }
    }
    reports {
        filters {
            excludes {
                classes(
                    "dev.lain.claudejb.frontend.*",
                    "dev.lain.claudejb.view.*",
                    "dev.lain.claudejb.model.bridge.*",
                    "dev.lain.claudejb.controller.bridge.*",
                    "dev.lain.claudejb.controller.commands.*",
                    "dev.lain.claudejb.controller.actions.*",
                    "dev.lain.claudejb.model.context.*",
                    "dev.lain.claudejb.controller.context.*",
                    "dev.lain.claudejb.controller.process.*",
                    "dev.lain.claudejb.controller.git.GitAvailability*",
                    "dev.lain.claudejb.controller.git.GitGateway*",
                    "dev.lain.claudejb.controller.git.GitHistoryService*",
                    "dev.lain.claudejb.controller.git.GitLogNavigator*",
                    "dev.lain.claudejb.util.*",
                    "dev.lain.claudejb.controller.vuln.OsvHttp*",
                    "dev.lain.claudejb.controller.vuln.VulnService*",
                    "dev.lain.claudejb.controller.mcp.IdeMcpService*",
                    "dev.lain.claudejb.controller.mcp.IdeToolCatalog*",
                    "dev.lain.claudejb.controller.mcp.tools.*",
                    "dev.lain.claudejb.controller.db.*",
                    "dev.lain.claudejb.controller.github.GitHubGateway*",
                )
            }
        }
        verify {
            rule("every gated package holds its floor") {
                groupBy.set(GroupingEntityType.PACKAGE)
                minBound(65)
                minBound(20, coverageUnits = CoverageUnit.BRANCH)
            }
            rule("gated code as a whole") {
                minBound(75)
                minBound(40, coverageUnits = CoverageUnit.BRANCH)
            }
        }
    }
}
