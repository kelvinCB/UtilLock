package app.utillock.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.utillock.android.R

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

private val UtilLockColors = darkColorScheme(
    primary = Orange400,
    onPrimary = Color.White,
    primaryContainer = Navy700,
    onPrimaryContainer = Cream200,
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

private fun TextStyle.brandHeadline() = copy(
    fontFamily = SpaceGrotesk,
    fontWeight = FontWeight.Bold,
)

private fun TextStyle.brandBody() = copy(
    fontFamily = Inter,
)

private val UtilLockTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.brandHeadline().copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.brandHeadline().copy(letterSpacing = (-0.4).sp),
        headlineMedium = base.headlineMedium.brandHeadline().copy(letterSpacing = (-0.3).sp),
        headlineSmall = base.headlineSmall.brandHeadline(),
        titleLarge = base.titleLarge.brandHeadline().copy(letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.brandHeadline().copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.brandHeadline().copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.brandBody().copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.brandBody().copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
        bodyLarge = base.bodyLarge.brandBody().copy(letterSpacing = 0.1.sp),
        bodyMedium = base.bodyMedium.brandBody().copy(letterSpacing = 0.1.sp),
        bodySmall = base.bodySmall.brandBody(),
    )
}

val UtilLockShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

object UtilLockGradients {
    val hero = Brush.linearGradient(listOf(Navy700, Navy600, Ink800))
    val heroSoft = Brush.linearGradient(listOf(Ink800, Ink700))
    val primaryButton = Brush.horizontalGradient(listOf(Orange400, Orange300))
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
