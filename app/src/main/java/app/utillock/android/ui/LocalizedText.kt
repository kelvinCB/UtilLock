package app.utillock.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun tr(spanish: String, english: String): String =
    if (LocalConfiguration.current.locales[0].language == "es") spanish else english

