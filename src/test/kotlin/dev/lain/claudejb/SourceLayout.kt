package dev.lain.claudejb

import java.io.File

internal object SourceLayout {

    const val PACKAGE_ROOT = "dev/lain/claudejb"

    val MODULE_NAMES = mapOf(
        "shared" to "dev.lain.claudejb.shared",
        "frontend" to "dev.lain.claudejb.frontend",
        "backend" to "dev.lain.claudejb.backend",
    )

    private val MODULES = listOf("backend", "frontend", "shared", "")

    fun roots(kind: String): List<File> = MODULES.mapNotNull { dir("${prefix(it)}src/main/$kind") }

    fun rootsOf(module: String, kind: String): List<File> = listOfNotNull(dir("${prefix(module)}src/main/$kind"))

    fun kotlinFiles(): List<File> = files(roots("kotlin"), setOf("kt"))

    fun jvmFiles(): List<File> = files(roots("kotlin") + roots("java"), setOf("kt", "java"))

    fun tsFiles(): List<File> = files(roots("ts"), setOf("ts"))

    fun files(roots: List<File>, extensions: Set<String>): List<File> =
        roots.flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension in extensions }.toList() }
            .sortedBy { it.invariantSeparatorsPath }

    fun pathInRoot(file: File): String {
        val root = (roots("kotlin") + roots("java") + roots("ts")).first { file.startsWith(it) }
        return file.relativeTo(root).invariantSeparatorsPath
    }

    fun packagePath(file: File): String =
        pathInRoot(file).removePrefix("$PACKAGE_ROOT/").substringBeforeLast('/', "")

    fun source(relative: String): File =
        roots("kotlin").map { File(it, "$PACKAGE_ROOT/$relative") }.firstOrNull { it.isFile }
            ?: kotlinFiles().filter { it.name == File(relative).name }.let { found ->
                check(found.size == 1) { "expected one ${File(relative).name} in ${roots("kotlin")}, found $found" }
                found.single()
            }

    fun mainDir(relative: String): File =
        MODULES.firstNotNullOfOrNull { dir("${prefix(it)}src/main/$relative") }
            ?: error("could not locate src/main/$relative in any module from ${File("").absolutePath}")

    fun mainFile(relative: String): File =
        MODULES.flatMap { listOf("${prefix(it)}src/main/$relative", "../${prefix(it)}src/main/$relative") }
            .map(::File).firstOrNull { it.isFile }
            ?: error("could not locate src/main/$relative in any module from ${File("").absolutePath}")

    fun descriptors(): List<File> =
        roots("resources").flatMap { root ->
            (root.listFiles().orEmpty().toList() + File(root, "META-INF").listFiles().orEmpty().toList())
                .filter { it.isFile && it.extension == "xml" }
        }.sortedBy { it.invariantSeparatorsPath }

    fun descriptor(name: String): File? = descriptors().firstOrNull { it.name == name }

    fun pluginDescriptor(): File = mainFile("resources/META-INF/plugin.xml")

    fun moduleDescriptor(module: String): File {
        val path = "${prefix(module)}src/main/resources/${MODULE_NAMES.getValue(module)}.xml"
        return sequenceOf(File(path), File("../$path")).firstOrNull { it.isFile } ?: File(path)
    }

    fun buildScripts(): List<File> =
        MODULES.map { File("${prefix(it)}build.gradle.kts") }.filter { it.isFile }

    private fun prefix(module: String): String = if (module.isEmpty()) "" else "$module/"

    private fun dir(path: String): File? = sequenceOf(File(path), File("../$path")).firstOrNull { it.isDirectory }
}
