package app.utillock.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy950 = Color(0xFF070B18)
val Navy900 = Color(0xFF0D1328)
val Navy800 = Color(0xFF151D38)
val Violet = Color(0xFF7C5CFC)
val VioletSoft = Color(0xFFA894FF)
val Mint = Color(0xFF4CE0B3)
val Amber = Color(0xFFFFC857)
val Error = Color(0xFFFF6B7A)

private val UtilLockColors = darkColorScheme(
    primary = VioletSoft,
    onPrimary = Navy950,
    primaryContainer = Color(0xFF30256D),
    secondary = Mint,
    onSecondary = Navy950,
    background = Navy950,
    onBackground = Color(0xFFF4F1FF),
    surface = Navy900,
    onSurface = Color(0xFFF4F1FF),
    surfaceVariant = Navy800,
    onSurfaceVariant = Color(0xFFC8C9D9),
    error = Error,
)

@Composable
fun UtilLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = UtilLockColors, content = content)
}

