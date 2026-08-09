package app.utillock.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val UtilLockColors = darkColorScheme(
    primary = Violet400,
    onPrimary = Ink950,
    primaryContainer = Violet900,
    onPrimaryContainer = Violet300,
    secondary = Aqua400,
    onSecondary = Ink950,
    secondaryContainer = Color(0xFF10352E),
    onSecondaryContainer = Aqua300,
    tertiary = Gold400,
    onTertiary = Ink950,
    tertiaryContainer = Color(0xFF3A2C10),
    onTertiaryContainer = Gold300,
    background = Ink950,
    onBackground = TextPrimary,
    surface = Ink900,
    onSurface = TextPrimary,
    surfaceVariant = Ink700,
    onSurfaceVariant = TextMuted,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink600,
    outline = Ink500,
    outlineVariant = Ink600,
    error = Rose400,
    onError = Ink950,
    errorContainer = Color(0xFF3A1620),
    onErrorContainer = Rose300,
)

private val UtilLockTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        bodyLarge = base.bodyLarge.copy(letterSpacing = 0.1.sp),
        bodyMedium = base.bodyMedium.copy(letterSpacing = 0.1.sp),
    )
}

val UtilLockShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Reusable gradients for the "premium" surfaces — hero cards, primary buttons, glows. */
object UtilLockGradients {
    val hero = Brush.linearGradient(listOf(Navy700, Navy600, Orange400.copy(alpha = 0.85f)))
    val heroSoft = Brush.linearGradient(listOf(Ink800, Ink700))
    val primaryButton = Brush.horizontalGradient(listOf(Violet600, Violet400))
    val premium = Brush.linearGradient(listOf(Gold600, Gold400, Gold300))
    val successGlow = Brush.linearGradient(listOf(Aqua600, Aqua400))
    val dangerGlow = Brush.linearGradient(listOf(Color(0xFFB43B4B), Rose400))
}

@Composable
fun UtilLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UtilLockColors,
        typography = UtilLockTypography,
        shapes = UtilLockShapes,
        content = content,
    )
}
