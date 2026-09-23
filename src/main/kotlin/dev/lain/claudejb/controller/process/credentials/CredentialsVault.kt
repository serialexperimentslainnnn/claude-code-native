package dev.lain.claudejb.controller.process.credentials

import com.intellij.openapi.application.ApplicationManager
import dev.lain.claudejb.controller.process.auth.AccountProfile
import dev.lain.claudejb.controller.process.auth.AuthCli
import dev.lain.claudejb.model.settings.SecretStore
import dev.lain.claudejb.util.thisLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jetbrains.annotations.TestOnly
import java.io.File

object CredentialsVault {

    private val log = thisLogger()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val EXPIRY_MARGIN_MS = 10 * 60 * 1000L

    private const val RENEW_COOLDOWN_MS = 5 * 60 * 1000L

    private const val ENV_REFRESH_TOKEN = "CLAUDE_CODE_OAUTH_REFRESH_TOKEN"
    private const val ENV_SCOPES = "CLAUDE_CODE_OAUTH_SCOPES"
    private const val ENV_SUBSCRIPTION_TYPE = "CLAUDE_CODE_SUBSCRIPTION_TYPE"
    private const val ENV_RATE_LIMIT_TIER = "CLAUDE_CODE_RATE_LIMIT_TIER"
    private const val ENV_ACCOUNT_UUID = "CLAUDE_CODE_ACCOUNT_UUID"
    private const val ENV_ORGANIZATION_UUID = "CLAUDE_CODE_ORGANIZATION_UUID"
    private const val ENV_USER_EMAIL = "CLAUDE_CODE_USER_EMAIL"

    @TestOnly
    @Volatile
    internal var homeOverride: File? = null

    fun credentialsFile(): File =
        File(homeOverride ?: File(System.getProperty("user.home").orEmpty()), ".claude/.credentials.json")

    private fun inertHere(): Boolean {
        if (homeOverride != null) return false
        return ApplicationManager.getApplication()?.isUnitTestMode ?: true
    }

    fun envOverlay(existing: Set<String>): Map<String, String> {
        if (SecretStore.OAUTH_TOKEN in existing || SecretStore.API_KEY in existing) return emptyMap()
        val oauth = oauthNode() ?: return emptyMap()
        val token = usableToken(oauth) ?: return emptyMap()
        return overlayFrom(token, oauth, accountNode())
    }

    internal fun overlayFrom(
        token: String,
        oauth: kotlinx.serialization.json.JsonObject,
        account: kotlinx.serialization.json.JsonObject?,
    ): Map<String, String> {
        val env = mutableMapOf(SecretStore.OAUTH_TOKEN to token)
        oauth.string("refreshToken")?.let { env[ENV_REFRESH_TOKEN] = it }
        oauth.strings("scopes")?.takeIf { it.isNotEmpty() }?.let { env[ENV_SCOPES] = it.joinToString(" ") }
        oauth.string("subscriptionType")?.let { env[ENV_SUBSCRIPTION_TYPE] = it }
        oauth.string("rateLimitTier")?.let { env[ENV_RATE_LIMIT_TIER] = it }
        account?.let {
            it.string("accountUuid")?.let { v -> env[ENV_ACCOUNT_UUID] = v }
            it.string("organizationUuid")?.let { v -> env[ENV_ORGANIZATION_UUID] = v }
            it.string("emailAddress")?.let { v -> env[ENV_USER_EMAIL] = v }
        }
        return env
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun kotlinx.serialization.json.JsonObject.strings(name: String): List<String>? =
        (this[name] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }

    private fun accountNode() = AccountProfile.storedAccountJson()?.let { blob ->
        runCatching { json.parseToJsonElement(blob).jsonObject }.getOrNull()
    }

    fun hasUsableToken(): Boolean = usableToken() != null

    fun canRenew(): Boolean = !renewBlocked() && renewable(oauthNode())

    private fun renewBlocked(): Boolean = System.currentTimeMillis() < renewBlockedUntil

    private fun renewable(oauth: kotlinx.serialization.json.JsonObject?): Boolean {
        if (oauth == null) return false
        if (oauth.string("refreshToken") == null) return false
        if (oauth.strings("scopes").isNullOrEmpty()) return false
        val expiresAt = oauth["refreshTokenExpiresAt"]?.jsonPrimitive?.longOrNull ?: return true
        return expiresAt - System.currentTimeMillis() > EXPIRY_MARGIN_MS
    }

    fun needsRenewal(): Boolean {
        val oauth = oauthNode()
        return (oauth == null || usableToken(oauth) == null) && !renewBlocked() && renewable(oauth)
    }

    fun renew(binary: File, baseEnv: Map<String, String>): Boolean = renewOnDisk(binary, baseEnv)

    internal fun refreshEnv(baseEnv: Map<String, String>): Map<String, String> =
        baseEnv.filterKeys { !it.startsWith(OAUTH_ENV_PREFIX, ignoreCase = true) }

    private const val OAUTH_ENV_PREFIX = "CLAUDE_CODE_OAUTH"

    @Volatile
    private var renewBlockedUntil = 0L

    @Volatile
    private var renewingOnDisk = false

    fun renewOnDisk(binary: File, baseEnv: Map<String, String>): Boolean {
        if (inertHere()) return false
        val blob = SecretStore.get(SecretStore.CREDENTIALS_JSON)?.takeIf { it.isNotBlank() } ?: return false
        val file = credentialsFile()
        if (file.exists()) {
            log.info("a credentials file is already on disk; harvesting it instead of planting one")
            return harvest() && hasUsableToken()
        }
        renewingOnDisk = true
        val renewed = try {
            when (plant(file, blob)) {
                Planting.FOREIGN -> false
                Planting.FAILED -> false.also { wipe(file) }
                Planting.PLANTED -> refreshPlanted(binary, baseEnv, file, blob)
            }
        } finally {
            renewingOnDisk = false
        }
        if (!renewed) log.warn("the on-disk credential refresh did not produce a usable token")
        renewBlockedUntil = if (renewed) 0L else System.currentTimeMillis() + RENEW_COOLDOWN_MS
        return renewed
    }

    internal enum class Planting { FOREIGN, FAILED, PLANTED }

    private fun refreshPlanted(binary: File, baseEnv: Map<String, String>, file: File, blob: String): Boolean {
        try {
            AuthCli.refreshUsingOwnFiles(binary, refreshEnv(baseEnv))
            AccountProfile.capture()
            return harvestNow() && hasUsableToken()
        } finally {
            settlePlanted(file, blob)
        }
    }

    internal fun settlePlanted(file: File, planted: String) {
        if (!file.isFile) return
        val now = runCatching { file.readText() }.getOrNull()
        if (now != null && (now.isBlank() || now == planted)) {
            wipe(file)
        } else {
            log.warn("the renewed credential stays on disk until the password safe accepts it")
        }
    }

    internal fun plant(file: File, blob: String): Planting {
        val created = runCatching {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }.onFailure { log.warn("could not create the credentials file for a refresh", it) }.getOrDefault(false)
        if (!created) {
            log.info("another writer holds the credentials file; leaving it untouched")
            return Planting.FOREIGN
        }
        return runCatching {
            val ownerOnly = file.setReadable(false, false) && file.setWritable(false, false) &&
                file.setReadable(true, true) && file.setWritable(true, true)
            if (!ownerOnly) {
                log.warn("could not make the credentials file owner-only; refusing to write a credential to it")
                file.delete()
                return Planting.FAILED
            }
            file.writeText(blob)
            Planting.PLANTED
        }.onFailure { log.warn("could not plant the credentials file for a refresh", it) }.getOrDefault(Planting.FAILED)
    }

    private fun wipe(file: File) {
        if (!file.isFile) return
        runCatching {
            file.writeText(" ".repeat(file.length().coerceAtMost(MAX_WIPE_BYTES).toInt()))
            file.delete()
        }.onFailure { log.warn("could not remove the planted credentials file", it) }
    }

    private const val MAX_WIPE_BYTES = 64L * 1024

    fun subscriptionType(): String? = oauthNode()?.get("subscriptionType")?.jsonPrimitive?.contentOrNull
        ?.takeIf { it.isNotBlank() }

    private fun oauthNode() = SecretStore.get(SecretStore.CREDENTIALS_JSON)?.let { blob ->
        runCatching { json.parseToJsonElement(blob).jsonObject["claudeAiOauth"]?.jsonObject }.getOrNull()
    }

    private fun usableToken(): String? = oauthNode()?.let(::usableToken)

    private fun usableToken(oauth: kotlinx.serialization.json.JsonObject): String? {
        val token = oauth["accessToken"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
        val expiresAt = oauth["expiresAt"]?.jsonPrimitive?.longOrNull ?: return null
        return token.takeIf { expiresAt - System.currentTimeMillis() > EXPIRY_MARGIN_MS }
    }

    fun harvest(): Boolean {
        if (inertHere()) return false
        if (renewingOnDisk) {
            log.debug { "not harvesting: a credential refresh is using the file right now" }
            return false
        }
        return harvestNow()
    }

    private fun harvestNow(): Boolean {
        val file = credentialsFile()
        if (!file.isFile) return false
        val text = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (text == null) {
            log.warn("credentials file present but unreadable/empty — leaving it alone")
            return false
        }
        if (!SecretStore.setVerified(SecretStore.CREDENTIALS_JSON, text)) {
            log.warn("the password safe did not keep the credential — leaving the file where it is")
            dev.lain.claudejb.model.settings.SafeAlarm.storeFailed()
            return false
        }
        runCatching {
            file.writeText(" ".repeat(text.length))
            file.delete()
        }.onFailure { log.warn("could not remove the credentials file after harvesting", it) }
        return true
    }

    fun clear() {
        if (inertHere()) return
        SecretStore.clear(SecretStore.CREDENTIALS_JSON)
        val file = credentialsFile()
        if (file.isFile) {
            runCatching { file.delete() }.onFailure { log.warn("could not delete the credentials file", it) }
        }
    }
}
