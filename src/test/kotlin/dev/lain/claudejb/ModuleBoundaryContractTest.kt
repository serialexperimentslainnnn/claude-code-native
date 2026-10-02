package dev.lain.claudejb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ModuleBoundaryContractTest {

    private val modules: Map<String, List<File>> = mapOf(
        SHARED to SourceLayout.files(SourceLayout.rootsOf(SHARED, "kotlin"), JVM),
        FRONTEND to SourceLayout.files(SourceLayout.rootsOf(FRONTEND, "kotlin"), JVM),
        BACKEND to SourceLayout.files(
            listOf(BACKEND, "").flatMap { SourceLayout.rootsOf(it, "kotlin") + SourceLayout.rootsOf(it, "java") },
            JVM,
        ),
    )

    private val owners: Map<String, Set<String>> = modules
        .flatMap { (module, files) -> files.map { fqn(it) to module } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.toSet() }

    @Test
    fun `the scan sees all three modules`() {
        modules.forEach { (module, files) -> assertTrue(files.isNotEmpty()) { "no sources found for the $module module" } }
    }

    @Test
    fun `every package lives in exactly one module, and the module it is named for`() {
        val split = owners.filterValues { it.size > 1 }.map { (pkg, where) -> "$pkg: $where" }
        assertEquals(emptyList<String>(), split) { "a package split across modules loads twice, from two classloaders" }
        val misplaced = modules.flatMap { (module, files) ->
            files.filterNot { homeOf(SourceLayout.packagePath(it)) == module }.map { "$module: ${SourceLayout.pathInRoot(it)}" }
        }
        assertEquals(emptyList<String>(), misplaced) {
            "dev.lain.claudejb.rpc is the shared contract, dev.lain.claudejb.frontend the frontend, everything else the backend."
        }
    }

    @Test
    fun `each module reaches only itself and the shared contract`() {
        val offenders = modules.flatMap { (module, files) ->
            files.flatMap { file ->
                references(file).filterNot { it.second in ALLOWED.getValue(module) }
                    .map { (ref, owner) -> "$module ${SourceLayout.pathInRoot(file)} -> $ref ($owner)" }
            }
        }
        assertEquals(emptyList<String>(), offenders) {
            "The frontend and the backend meet only through dev.lain.claudejb.rpc. A type that crosses any other way is a " +
                "class the other side of a remote session does not have."
        }
    }

    @Test
    fun `the backend never touches the browser`() {
        val offenders = modules.getValue(BACKEND).flatMap { file ->
            MainSources.codeOf(file).withIndex().filter { (_, line) -> BROWSER.containsMatchIn(line) }
                .map { (index, line) -> "${SourceLayout.pathInRoot(file)}:${index + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList<String>(), offenders) {
            "JCEF lives in the frontend only; on a remote host the backend has no browser to reach."
        }
    }

    private fun references(file: File): List<Pair<String, String>> =
        MainSources.codeOf(file)
            .filterNot { it.trimStart().startsWith("package ") }
            .flatMap { line -> REFERENCE.findAll(line).map { it.value }.toList() }
            .distinct()
            .map { ref -> ref to ownerOf(ref) }

    private fun ownerOf(ref: String): String =
        owners.keys.filter { ref == it || ref.startsWith("$it.") }.maxByOrNull { it.length }
            ?.let { owners.getValue(it).sorted().joinToString("+") }
            ?: UNKNOWN

    private fun fqn(file: File): String =
        listOf(ROOT_PACKAGE, SourceLayout.packagePath(file).replace('/', '.')).filter { it.isNotEmpty() }.joinToString(".")

    private fun homeOf(packagePath: String): String = when {
        packagePath == "rpc/backend" || packagePath.startsWith("rpc/backend/") -> BACKEND
        packagePath == "rpc" || packagePath.startsWith("rpc/") -> SHARED
        packagePath == "frontend" || packagePath.startsWith("frontend/") -> FRONTEND
        else -> BACKEND
    }

    private companion object {
        const val SHARED = "shared"
        const val FRONTEND = "frontend"
        const val BACKEND = "backend"
        const val UNKNOWN = "no module"
        const val ROOT_PACKAGE = "dev.lain.claudejb"

        val JVM = setOf("kt", "java")

        val ALLOWED = mapOf(
            SHARED to setOf(SHARED),
            FRONTEND to setOf(FRONTEND, SHARED),
            BACKEND to setOf(BACKEND, SHARED),
        )

        val REFERENCE = Regex("""\bdev\.lain\.claudejb(?:\.[a-z_]\w*)*""")

        val BROWSER = Regex("""\bcom\.intellij\.ui\.jcef\b|\borg\.cef\b""")
    }
}
