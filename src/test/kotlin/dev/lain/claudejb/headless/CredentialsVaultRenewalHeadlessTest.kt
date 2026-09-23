package dev.lain.claudejb.headless

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.lain.claudejb.controller.process.credentials.CredentialsVault
import dev.lain.claudejb.model.settings.SecretStore
import java.io.File
import java.nio.file.Files

class CredentialsVaultRenewalHeadlessTest : BasePlatformTestCase() {

    private val file get() = CredentialsVault.credentialsFile()
    private lateinit var home: File

    override fun setUp() {
        super.setUp()
        home = Files.createTempDirectory("claudejb-renew").toFile()
        CredentialsVault.homeOverride = home
        SecretStore.storeOverride = mutableMapOf()
    }

    override fun tearDown() {
        try {
            SecretStore.storeOverride = null
            CredentialsVault.homeOverride = null
            home.deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private class RefusingStore(seed: Map<String, String>) : HashMap<String, String>(seed) {
        override fun put(key: String, value: String): String? = get(key)
    }

    fun `test planting never takes over a file another writer created`() {
        file.parentFile?.mkdirs()
        file.writeText("someone else's credential")

        assertEquals(CredentialsVault.Planting.FOREIGN, CredentialsVault.plant(file, "planted"))
        CredentialsVault.settlePlanted(file, "planted")
        assertEquals("someone else's credential", file.readText())
    }

    fun `test a planted file holds the blob`() {
        assertEquals(CredentialsVault.Planting.PLANTED, CredentialsVault.plant(file, "planted"))
        assertEquals("planted", file.readText())
    }

    fun `test an unrotated planted file is wiped`() {
        assertEquals(CredentialsVault.Planting.PLANTED, CredentialsVault.plant(file, "planted"))
        CredentialsVault.settlePlanted(file, "planted")
        assertFalse(file.exists())
    }

    fun `test a rotated credential the safe did not take stays on disk`() {
        SecretStore.storeOverride = RefusingStore(mapOf(SecretStore.CREDENTIALS_JSON to "old"))
        assertEquals(CredentialsVault.Planting.PLANTED, CredentialsVault.plant(file, "old"))
        file.writeText("rotated")

        assertFalse(CredentialsVault.harvest())
        CredentialsVault.settlePlanted(file, "old")

        assertEquals("rotated", file.readText())
        assertEquals("old", SecretStore.get(SecretStore.CREDENTIALS_JSON))
    }

    fun `test a rotated credential the safe took leaves nothing on disk`() {
        SecretStore.set(SecretStore.CREDENTIALS_JSON, "old")
        assertEquals(CredentialsVault.Planting.PLANTED, CredentialsVault.plant(file, "old"))
        file.writeText("rotated")

        assertTrue(CredentialsVault.harvest())
        CredentialsVault.settlePlanted(file, "old")

        assertFalse(file.exists())
        assertEquals("rotated", SecretStore.get(SecretStore.CREDENTIALS_JSON))
    }
}
