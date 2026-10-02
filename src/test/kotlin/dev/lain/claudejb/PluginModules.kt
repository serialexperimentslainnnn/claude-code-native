package dev.lain.claudejb

import java.io.File

internal object PluginModules {

    fun content(): Map<String, String> =
        CONTENT.findAll(SourceLayout.pluginDescriptor().readText()).flatMap { MODULE.findAll(it.value) }
            .associate { it.groupValues[1] to it.value }

    fun descriptorOf(module: String): File? = SourceLayout.descriptor("$module.xml")

    fun dependencies(descriptor: File): String =
        DEPENDENCIES.findAll(descriptor.readText()).joinToString("\n") { it.value }

    fun pluginDependencies(descriptor: File): List<String> =
        PLUGIN.findAll(dependencies(descriptor)).map { it.groupValues[1] }.toList()

    fun moduleDependencies(descriptor: File): List<String> =
        MODULE.findAll(dependencies(descriptor)).map { it.groupValues[1] }.toList()

    fun isOptional(entry: String): Boolean = !REQUIRED.containsMatchIn(entry) && REQUIRED_IF_AVAILABLE !in entry

    fun alwaysLoaded(): List<File> =
        listOf(SourceLayout.pluginDescriptor()) +
            content().filterValues { !isOptional(it) }.keys.mapNotNull { descriptorOf(it) }

    fun declaring(pluginId: String): List<File> = SourceLayout.descriptors().filter { pluginId in pluginDependencies(it) }

    fun moduleName(descriptor: File): String = descriptor.name.removeSuffix(".xml")

    const val REQUIRED_IF_AVAILABLE = "required-if-available"

    private val CONTENT = Regex("""<content\b[^>]*>[\s\S]*?</content>""")
    private val DEPENDENCIES = Regex("""<dependencies\b[^>]*>[\s\S]*?</dependencies>""")
    private val MODULE = Regex("""<module\s+name="([^"]+)"[^>]*>""")
    private val PLUGIN = Regex("""<plugin\s+id="([^"]+)"[^>]*>""")
    private val REQUIRED = Regex("""\bloading\s*=\s*"(required|embedded)"""")
}
