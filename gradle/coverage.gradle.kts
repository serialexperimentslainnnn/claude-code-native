import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

apply(plugin = "jacoco")

configure<JacocoPluginExtension> {
    toolVersion = "0.8.15"
}

val platformCoverage = layout.buildDirectory.file("jacoco/test.exec")

tasks.named<Test>("test") {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
        setDestinationFile(platformCoverage.map { it.asFile })
    }
}

tasks.matching { it.name.startsWith("kover") && (it.name.contains("Report") || it.name.contains("Verify")) }.configureEach {
    dependsOn("test")
}

dependencies {
    listOf("shared", "frontend", "backend", "git", "github", "java", "intellilang", "terminal", "bookmarks").forEach {
        "kover"(project(":$it"))
    }
}

configure<KoverProjectExtension> {
    currentProject {
        instrumentation {
            disabledForTestTasks.addAll("checkDrift", "uiTest", "bench", "test")
        }
        sources {
            excludedSourceSets.add("uiTest")
        }
    }
    reports {
        total {
            additionalBinaryReports.add(platformCoverage.map { it.asFile })
        }
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
