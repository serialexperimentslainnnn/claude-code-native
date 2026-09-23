import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension

val pluginModules = listOf("shared", "frontend", "backend")

configure<DetektExtension> {
    buildUponDefaultConfig.set(true)
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline.set(file("config/detekt/baseline.xml"))
    source.setFrom(pluginModules.map { "$it/src/main/kotlin" } + "src/test/kotlin")
    parallel.set(true)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget.set("25")
    reports {
        html.required.set(true)
        sarif.required.set(true)
        checkstyle.required.set(false)
        markdown.required.set(false)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("25")
}

configure<SpotlessExtension> {
    kotlin {
        target("src/**/*.kt", *pluginModules.map { "$it/src/**/*.kt" }.toTypedArray())
        ktlint("1.8.0").editorConfigOverride(
            mapOf(
                "max_line_length" to "140",
                "ij_kotlin_allow_trailing_comma" to "true",
                "ij_kotlin_allow_trailing_comma_on_call_site" to "true",
                "ktlint_standard_function-signature" to "disabled",
                "ktlint_standard_class-signature" to "disabled",
                "ktlint_standard_function-expression-body" to "disabled",
                "ktlint_standard_max-line-length" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
            ),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts", "*/build.gradle.kts", "gradle/*.gradle.kts")
        ktlint("1.8.0")
    }
}
