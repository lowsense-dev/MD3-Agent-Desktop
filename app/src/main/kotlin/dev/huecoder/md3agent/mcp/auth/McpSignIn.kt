package dev.huecoder.md3agent.mcp.auth

import dev.huecoder.md3agent.data.settings.McpServer
import dev.huecoder.md3agent.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

sealed interface SignInStatus {
    data object Idle : SignInStatus
    data class InProgress(val serverId: String) : SignInStatus
    data class Failed(val serverId: String, val message: String) : SignInStatus
}

class McpSignIn(private val oauth: McpOAuth, private val settings: SettingsRepository) {
    private val pending = AtomicReference<PendingSignIn?>(null)
    private val _status = MutableStateFlow<SignInStatus>(SignInStatus.Idle)

    val status: StateFlow<SignInStatus> = _status.asStateFlow()

    /**
     * Starts a sign-in and returns the authorization URL to open in the browser. The browser comes back to a loopback
     * listener, which hands the reply to [onReply]; the caller passes it on to [complete].
     */
    suspend fun begin(server: McpServer, resourceMetadataUrl: String?, onReply: (Map<String, String>) -> Unit): String {
        _status.value = SignInStatus.InProgress(server.id)
        pending.getAndSet(null)?.loopback?.close()
        val loopback = OAuthLoopback.start(onReply)
        return try {
            val redirectUri = loopback.redirectUri
            val authServer = oauth.discover(server.url, resourceMetadataUrl)
            val client = oauth.register(authServer, redirectUri, CLIENT_NAME)
            val pkce = Pkce.create()
            val state = "${server.id}$STATE_SEPARATOR${UUID.randomUUID()}"
            pending.set(PendingSignIn(server.id, authServer, client, pkce, state, redirectUri, loopback))
            oauth.authorizationUrl(authServer, client, redirectUri, pkce, state)
        } catch (e: IOException) {
            loopback.close()
            _status.value = SignInStatus.Failed(server.id, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    suspend fun complete(reply: Map<String, String>) {
        val state = reply["state"]
        val signIn = pending.get()
        if (signIn == null) {
            state?.substringBefore(STATE_SEPARATOR)?.let { _status.value = SignInStatus.Failed(it, "The sign-in was interrupted, try again") }
            return
        }
        if (state != signIn.state || !pending.compareAndSet(signIn, null)) return
        signIn.loopback.close()
        val failure = when {
            reply["error"] != null -> reply["error_description"] ?: reply["error"]
            reply["code"] == null -> "The sign-in reply has no code"
            else -> null
        }
        if (failure != null) {
            _status.value = SignInStatus.Failed(signIn.serverId, failure)
            return
        }
        try {
            val session = oauth.exchange(signIn.server, signIn.client, reply["code"].orEmpty(), signIn.redirectUri, signIn.pkce)
            settings.updateTools { tools ->
                tools.copy(mcpServers = tools.mcpServers.map { if (it.id == signIn.serverId) it.copy(oauth = session) else it })
            }
            _status.value = SignInStatus.Idle
        } catch (e: IOException) {
            _status.value = SignInStatus.Failed(signIn.serverId, e.message ?: e.javaClass.simpleName)
        }
    }

    fun dismissFailure() {
        if (_status.value is SignInStatus.Failed) _status.value = SignInStatus.Idle
    }

    private class PendingSignIn(
        val serverId: String,
        val server: AuthServer,
        val client: OAuthClient,
        val pkce: Pkce,
        val state: String,
        val redirectUri: String,
        val loopback: OAuthLoopback,
    )

    companion object {
        private const val CLIENT_NAME = "MD3 Agent"
        private const val STATE_SEPARATOR = '.'
    }
}
