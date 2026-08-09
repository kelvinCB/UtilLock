package app.utillock.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val LANGUAGE_PREFS = "ui_preferences"
private const val LANGUAGE_KEY = "language"

private object LanguagePreference {
    var language by mutableStateOf("es")
}

@Composable
private fun rememberLanguagePreference() {
    val context = LocalContext.current
    if (LanguagePreference.language == "es") {
        LanguagePreference.language = context.getSharedPreferences(LANGUAGE_PREFS, 0)
            .getString(LANGUAGE_KEY, "es") ?: "es"
    }
}

@Composable
fun currentLanguage(): String {
    rememberLanguagePreference()
    return LanguagePreference.language
}

fun setLanguage(context: android.content.Context, language: String) {
    LanguagePreference.language = language
    context.getSharedPreferences(LANGUAGE_PREFS, 0)
        .edit()
        .putString(LANGUAGE_KEY, language)
        .apply()
}

@Composable
fun tr(spanish: String, english: String): String {
    rememberLanguagePreference()
    return if (LanguagePreference.language == "es") spanish else english
}

