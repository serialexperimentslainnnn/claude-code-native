package dev.lain.claudejb.frontend.window

import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class ClipImage(val mime: String, val bytes: ByteArray)

internal object ClipboardCli {

    private val log = logger<ClipboardCli>()

    fun isLinux() = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    fun findExecutable(name: String): String? {
        val dirs = LinkedHashSet<String>()
        System.getenv("PATH")?.split(File.pathSeparatorChar)?.forEach { if (it.isNotBlank()) dirs.add(it) }
        dirs.addAll(COMMON_BIN_DIRS)
        return dirs.map { File(it, name) }.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }

    fun image(): ClipImage? {
        if (!isLinux()) return null
        val wlPaste = findExecutable("wl-paste")
        val xclip = findExecutable("xclip")
        wlPaste?.let { typed(listOf(it, "--list-types")) { type -> listOf(it, "-t", type) } }?.let { return it }
        xclip?.let { x ->
            typed(listOf(x, "-selection", "clipboard", "-t", "TARGETS", "-o")) { type ->
                listOf(x, "-selection", "clipboard", "-t", type, "-o")
            }
        }?.let { return it }
        wlPaste?.let { fromUriList(listOf(it, "-t", "text/uri-list")) }?.let { return it }
        return xclip?.let { fromUriList(listOf(it, "-selection", "clipboard", "-t", "text/uri-list", "-o")) }
    }

    fun text(): String? {
        val type = textType() ?: return null
        val command = textCommand(type) ?: return null
        return run(command)?.toString(Charsets.UTF_8)?.takeIf { it.isNotEmpty() }
    }

    fun textType(): String? {
        if (!isLinux()) return null
        findExecutable("wl-paste")?.let { preferredTextType(listTypes(listOf(it, "--list-types")))?.let { t -> return t } }
        return findExecutable("xclip")?.let { preferredTextType(listTypes(listOf(it, "-selection", "clipboard", "-t", "TARGETS", "-o"))) }
    }

    fun preferredTextType(types: List<String>): String? {
        fun first(p: (String) -> Boolean) = types.firstOrNull(p)
        return first { it.equals("text/plain;charset=utf-8", ignoreCase = true) }
            ?: first { it == "UTF8_STRING" }
            ?: first { it.equals("text/plain", ignoreCase = true) }
            ?: first { it == "STRING" || it == "TEXT" }
            ?: first {
                it.startsWith("text/", ignoreCase = true) &&
                    !it.equals("text/uri-list", ignoreCase = true) &&
                    !it.startsWith("text/html", ignoreCase = true)
            }
    }

    fun installHint(): String {
        val release = runCatching { File("/etc/os-release").readText().lowercase() }.getOrDefault("")
        return when {
            listOf("fedora", "rhel", "centos", "rocky", "alma").any { it in release } -> "sudo dnf install wl-clipboard"
            listOf("debian", "ubuntu", "mint", "pop").any { it in release } -> "sudo apt install wl-clipboard"
            "arch" in release || "manjaro" in release -> "sudo pacman -S wl-clipboard"
            "opensuse" in release || "suse" in release -> "sudo zypper install wl-clipboard"
            else -> "install 'wl-clipboard' (or 'xclip') with your package manager"
        }
    }

    fun imageType(types: List<String>): String? =
        types.firstOrNull { it == "image/png" }
            ?: types.firstOrNull { it == "image/jpeg" || it == "image/jpg" }
            ?: types.firstOrNull { it.startsWith("image/") }

    fun mimeOfPath(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }

    private fun textCommand(type: String): List<String>? =
        findExecutable("wl-paste")?.let { listOf(it, "-t", type, "-n") }
            ?: findExecutable("xclip")?.let { listOf(it, "-selection", "clipboard", "-t", type, "-o") }

    private fun typed(listCommand: List<String>, readCommand: (String) -> List<String>): ClipImage? {
        val type = imageType(listTypes(listCommand)) ?: return null
        val bytes = run(readCommand(type)) ?: return null
        return ClipImage(if (type == "image/jpg") "image/jpeg" else type, bytes)
    }

    private fun fromUriList(command: List<String>): ClipImage? {
        val listing = run(command)?.toString(Charsets.UTF_8) ?: return null
        return listing.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull(::pathOf)
            .firstNotNullOfOrNull(::imageFile)
    }

    private fun imageFile(path: String): ClipImage? {
        val mime = mimeOfPath(path) ?: return null
        val file = File(path).takeIf { it.isFile && it.length() in 1..MAX_FILE_BYTES } ?: return null
        return runCatching { ClipImage(mime, file.readBytes()) }.getOrNull()
    }

    private fun pathOf(uri: String): String? = runCatching {
        when {
            uri.startsWith("file://") -> File(java.net.URI(uri)).path
            uri.startsWith("/") -> uri
            else -> null
        }
    }.getOrNull()

    private fun listTypes(command: List<String>): List<String> =
        run(command)?.toString(Charsets.UTF_8)?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList()
            .orEmpty()

    private fun run(command: List<String>): ByteArray? = runCatching {
        val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val reader = CompletableFuture.supplyAsync { runCatching { process.inputStream.readBytes() }.getOrNull() }
        val bytes = try {
            reader.get(READ_SECONDS, TimeUnit.SECONDS)
        } catch (e: TimeoutException) {
            log.debug("Clipboard helper ${command.firstOrNull()} timed out; killing it", e)
            process.destroyForcibly()
            reader.cancel(true)
            return@runCatching null
        }
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching null
        }
        bytes?.takeIf { process.exitValue() == 0 && it.isNotEmpty() }
    }.getOrNull()

    private const val READ_SECONDS = 3L

    private const val MAX_FILE_BYTES = 20L * 1024 * 1024

    private val COMMON_BIN_DIRS: List<String> by lazy {
        val home = System.getProperty("user.home").orEmpty()
        listOf(
            "/usr/bin", "/bin", "/usr/local/bin", "/usr/sbin", "/sbin",
            "/run/current-system/sw/bin", "/var/lib/flatpak/exports/bin", "/snap/bin",
            "/opt/homebrew/bin", "/usr/local/sbin", "$home/.local/bin", "$home/bin",
        )
    }
}
