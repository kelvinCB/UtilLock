package app.utillock.android.data

import android.util.Base64
import app.utillock.android.BuildConfig
import app.utillock.android.model.ChallengeVerdict
import app.utillock.android.model.LogicChallenge
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class BackendClient(private val sessions: SessionRepository) {
    val configured: Boolean get() = sessions.isConfigured()

    suspend fun startChallenge(): LogicChallenge? {
        val locale = if (Locale.getDefault().language == "es") "es" else "en"
        val result = call("challenge-start", JSONObject().put("locale", locale)) ?: return null
        if (result.optString("mode") == "local_wait") {
            return LogicChallenge(
                id = result.optString("session_id", "local"),
                title = result.optString("title", if (locale == "es") "Resuelve el ejercicio" else "Solve the exercise"),
                pseudocode = result.optString("pseudocode"),
                expectedAnswer = result.optString("expected_answer"),
                expiresAtEpochMs = result.optLong("expires_at") * 1_000,
                remote = false,
                localAvailableAtEpochMs = result.optLong("local_available_at") * 1_000,
            )
        }
        return LogicChallenge(
            id = result.getString("session_id"),
            title = result.getString("title"),
            pseudocode = result.getString("pseudocode"),
            expiresAtEpochMs = result.getLong("expires_at") * 1_000,
            remote = true,
            attemptsRemaining = result.optInt("attempts_remaining", 3),
        )
    }

    suspend fun evaluateChallenge(challengeId: String, image: File): ChallengeVerdict? {
        if (image.length() > 1_500_000) {
            val message = if (Locale.getDefault().language == "es") "La foto supera 1.5 MB." else "The photo is larger than 1.5 MB."
            return ChallengeVerdict(false, message, 3)
        }
        val body = JSONObject()
            .put("session_id", challengeId)
            .put("mime_type", "image/jpeg")
            .put("image_base64", Base64.encodeToString(image.readBytes(), Base64.NO_WRAP))
        val result = call("challenge-evaluate", body) ?: return null
        return ChallengeVerdict(
            accepted = result.optBoolean("accepted"),
            feedback = result.optString("feedback"),
            attemptsRemaining = result.optInt("attempts_remaining"),
        )
    }

    suspend fun verifyPurchase(purchaseToken: String, productId: String): Boolean {
        val result = call(
            "billing-verify",
            JSONObject().put("purchase_token", purchaseToken).put("product_id", productId),
        ) ?: return false
        return result.optBoolean("active")
    }

    suspend fun deleteAccount(): Boolean = call("account-delete", JSONObject())?.optBoolean("deleted") == true

    private suspend fun call(functionName: String, body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val session = sessions.validSession() ?: return@withContext null
        runCatching {
            val endpoint = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/$functionName"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            }
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val payload = JSONObject(stream.bufferedReader().use { it.readText() })
            if (connection.responseCode !in 200..299) return@runCatching null
            payload
        }.getOrNull()
    }
}
