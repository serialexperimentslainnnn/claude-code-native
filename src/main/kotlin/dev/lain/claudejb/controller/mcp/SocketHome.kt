package dev.lain.claudejb.controller.mcp

import dev.lain.claudejb.mcp.StdioBridge
import dev.lain.claudejb.model.session.launch.IdeServer
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries

internal class SocketHome private constructor(val dir: Path) {

    fun socket(server: IdeServer): Path = dir.resolve("${server.key}$SOCKET_SUFFIX")

    fun writeToken(token: String) {
        val staging = dir.resolve(StdioBridge.TOKEN_FILE + STAGING_SUFFIX)
        if (POSIX) {
            if (Files.notExists(staging)) {
                Files.createFile(staging, PosixFilePermissions.asFileAttribute(TOKEN_OWNER_ONLY))
            } else {
                Files.setPosixFilePermissions(staging, TOKEN_OWNER_ONLY)
            }
        }
        Files.writeString(staging, token, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        Files.move(staging, dir.resolve(StdioBridge.TOKEN_FILE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    fun remove() {
        runCatching { dir.listDirectoryEntries().forEach { it.deleteIfExists() } }
        runCatching { dir.deleteIfExists() }
    }

    companion object {

        const val PARENT = "claude-ide-mcp"
        const val MAX_SOCKET_PATH = 100
        private const val SOCKET_SUFFIX = ".sock"
        private const val ID_BYTES = 16
        private val POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
        private val OWNER_ONLY = PosixFilePermissions.fromString("rwx------")
        private val TOKEN_OWNER_ONLY = PosixFilePermissions.fromString("rw-------")
        private const val STAGING_SUFFIX = ".next"

        fun create(bases: List<Path>): SocketHome {
            val id = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(ID_BYTES).also(SecureRandom()::nextBytes))
            val longestName = IdeServer.entries.maxOf { it.key.length } + SOCKET_SUFFIX.length
            val base = bases.firstOrNull { fits(it, id, longestName) }
                ?: error("no temporary directory short enough for a Unix socket path")
            val parent = base.resolve(PARENT)
            val dir = parent.resolve(id)
            if (POSIX) {
                Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(OWNER_ONLY))
                Files.setPosixFilePermissions(parent, OWNER_ONLY)
                Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(OWNER_ONLY))
            } else {
                Files.createDirectories(dir)
            }
            return SocketHome(dir)
        }

        private fun fits(base: Path, id: String, longestName: Int): Boolean =
            base.toAbsolutePath().toString().length + PARENT.length + id.length + longestName + 3 <= MAX_SOCKET_PATH
    }
}
