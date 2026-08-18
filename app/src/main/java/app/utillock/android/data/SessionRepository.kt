package app.utillock.android.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.net.toUri
import app.utillock.android.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Context.sessionDataStore by preferencesDataStore(name = "session")

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
)

class SessionRepository(private val context: Context) {
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val expiresKey = longPreferencesKey("expires_at")
    private val userKey = stringPreferencesKey("user_id")
    private val mutex = Mutex()
    private val mutableLinked = MutableStateFlow(false)
    val linked: StateFlow<Boolean> = mutableLinked.asStateFlow()

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO).launch {
            read()?.let { mutableLinked.value = !jwtIsAnonymous(it.accessToken) }
        }
    }

    fun isConfigured(): Boolean = BuildConfig.SUPABASE_URL.isNotBlank() &&
        BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    suspend fun validSession(): AuthSession? = mutex.withLock {
        if (!isConfigured()) return@withLock null
        val stored = read()
        if (stored == null) return@withLock createAnonymousSession()
        if (stored.expiresAtEpochSeconds > System.currentTimeMillis() / 1_000 + 60) return@withLock stored
        refresh(stored.refreshToken) ?: createAnonymousSession()
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
        mutableLinked.value = false
    }

    suspend fun googleLinkUrl(): String? {
        val session = validSession() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val redirect = Uri.encode(AUTH_REDIRECT)
                val endpoint = BuildConfig.SUPABASE_URL.trimEnd('/') +
                    "/auth/v1/user/identities/authorize?provider=google&redirect_to=$redirect&skip_http_redirect=true"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                }
                if (connection.responseCode !in 200..299) return@runCatching null
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optString("url")
                    .takeIf(String::isNotBlank)
            }.getOrNull()
        }
    }

    suspend fun importAuthRedirect(uri: Uri?): Boolean {
        if (uri?.scheme != "utillock" || uri.host != "auth-callback") return false
        val values = "https://auth.local/?${uri.fragment.orEmpty()}".toUri()
        val access = values.getQueryParameter("access_token") ?: return false
        val refresh = values.getQueryParameter("refresh_token") ?: return false
        val expiresIn = values.getQueryParameter("expires_in")?.toLongOrNull() ?: 3_600
        val imported = AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = System.currentTimeMillis() / 1_000 + expiresIn,
            userId = jwtUserId(access),
        )
        persist(imported)
        mutableLinked.value = !jwtIsAnonymous(access)
        return true
    }

    private suspend fun read(): AuthSession? {
        val values = context.sessionDataStore.data.first()
        val access = values[accessKey] ?: return null
        val refresh = values[refreshKey] ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = values[expiresKey] ?: jwtExpiry(access),
            userId = values[userKey].orEmpty(),
        )
    }

    private suspend fun createAnonymousSession(): AuthSession? = request(
        path = "/auth/v1/signup",
        body = JSONObject(),
    )

    private suspend fun refresh(refreshToken: String): AuthSession? = request(
        path = "/auth/v1/token?grant_type=refresh_token",
        body = JSONObject().put("refresh_token", refreshToken),
    )

    private suspend fun request(path: String, body: JSONObject): AuthSession? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(BuildConfig.SUPABASE_URL.trimEnd('/') + path).openConnection() as HttpURLConnection)
            connection.requestMethod = "POST"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) return@runCatching null
            val payload = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val session = AuthSession(
                accessToken = payload.getString("access_token"),
                refreshToken = payload.getString("refresh_token"),
                expiresAtEpochSeconds = payload.optLong("expires_at", jwtExpiry(payload.getString("access_token"))),
                userId = payload.optJSONObject("user")?.optString("id").orEmpty(),
            )
            context.sessionDataStore.edit { values ->
                values[accessKey] = session.accessToken
                values[refreshKey] = session.refreshToken
                values[expiresKey] = session.expiresAtEpochSeconds
                values[userKey] = session.userId
            }
            mutableLinked.value = !jwtIsAnonymous(session.accessToken)
            session
        }.getOrNull()
    }

    private suspend fun persist(session: AuthSession) {
        context.sessionDataStore.edit { values ->
            values[accessKey] = session.accessToken
            values[refreshKey] = session.refreshToken
            values[expiresKey] = session.expiresAtEpochSeconds
            values[userKey] = session.userId
        }
    }

    private fun jwtExpiry(jwt: String): Long = runCatching {
        val part = jwt.split('.')[1]
        val decoded = Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(decoded)).optLong("exp")
    }.getOrDefault(0)

    private fun jwtUserId(jwt: String): String = jwtPayload(jwt).optString("sub")
    private fun jwtIsAnonymous(jwt: String): Boolean = jwtPayload(jwt).optBoolean("is_anonymous", true)

    private fun jwtPayload(jwt: String): JSONObject = runCatching {
        val part = jwt.split('.')[1]
        val decoded = Base64.decode(part, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        JSONObject(String(decoded))
    }.getOrDefault(JSONObject())

    private companion object {
        const val AUTH_REDIRECT = "utillock://auth-callback"
    }
}
