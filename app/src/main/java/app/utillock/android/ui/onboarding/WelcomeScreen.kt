package app.utillock.android.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.utillock.android.R
import app.utillock.android.ui.theme.Aqua300
import app.utillock.android.ui.theme.Orange300
import app.utillock.android.ui.theme.Orange400
import app.utillock.android.ui.theme.OrangeGlow
import app.utillock.android.ui.theme.SpaceGrotesk
import app.utillock.android.ui.theme.TextMuted
import app.utillock.android.ui.theme.TextPrimary
import app.utillock.android.ui.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Floating distraction bubble — particle-like elliptical drift, same family as Uli sparkles. */
private data class FloatBubble(
    val iconRes: Int,
    val orbit: Float,
    val phase: Float,
    val speed: Float,
    val sizeDp: Float,
    val depthAmp: Float,
)

private data class Sparkle(
    val orbit: Float,
    val phase: Float,
    val speed: Float,
    val size: Float,
    val hex: Boolean,
)

private val FloatBubbles = listOf(
    FloatBubble(R.drawable.orbit_3d_instagram, 0.40f, 0.2f, 0.42f, 54f, 0.75f),
    FloatBubble(R.drawable.orbit_3d_youtube, 0.46f, 1.3f, 0.35f, 50f, 0.70f),
    FloatBubble(R.drawable.orbit_3d_game, 0.38f, 2.4f, 0.48f, 52f, 0.80f),
    FloatBubble(R.drawable.orbit_3d_tiktok, 0.44f, 3.6f, 0.38f, 48f, 0.72f),
    FloatBubble(R.drawable.orbit_3d_chat, 0.41f, 4.7f, 0.45f, 50f, 0.78f),
    FloatBubble(R.drawable.orbit_3d_x, 0.48f, 5.6f, 0.32f, 46f, 0.68f),
)

private val Sparkles = listOf(
    Sparkle(0.30f, 0.4f, 0.55f, 5.5f, true),
    Sparkle(0.34f, 1.6f, 0.40f, 3.8f, false),
    Sparkle(0.36f, 2.8f, 0.62f, 6.2f, true),
    Sparkle(0.32f, 4.1f, 0.48f, 3.4f, false),
    Sparkle(0.38f, 5.2f, 0.35f, 4.8f, true),
    Sparkle(0.33f, 0.9f, 0.58f, 3.6f, false),
)

private const val TERMS_URL = "https://utillock.app/terms"
private const val PRIVACY_URL = "https://utillock.app/privacy"

/**
 * Post-splash welcome — Ink mascot with soft-3D floating distraction bubbles
 * (particle-style motion like Uli's orange sparkles; no rigid orbit ring).
 */
@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onAlreadyMember: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize()) {
        WelcomeSpaceBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            Text(
                text = tr("Bienvenido a UtilLock", "Welcome to UtilLock"),
                color = TextMuted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = tr(
                    "Recupera el control\nde tu atención.",
                    "Regain control\nof your attention.",
                ),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp,
                ),
                textAlign = TextAlign.Center,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                WelcomeHeroAlive()
            }

            AliveStartButton(
                text = tr("Empezar ahora", "Get started"),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            Text(
                text = tr("Ya soy miembro", "Already a member"),
                color = Orange300,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onAlreadyMember() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )

            Spacer(Modifier.height(20.dp))
            LegalFooter(
                onOpenTerms = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL))) },
                onOpenPrivacy = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) },
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Saturated pill CTA — gradient fill, outer bloom, glass highlight (welcome mock). */
@Composable
private fun AliveStartButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val loop = rememberInfiniteTransition(label = "ctaGlow")
    val glowPulse by loop.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    val fill = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFB15A),
            OrangeGlow,
            Orange400,
            Color(0xFFE8681A),
        ),
    )

    Box(
        modifier = modifier
            .height(60.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.985f else 1f
                scaleY = if (pressed) 0.985f else 1f
            }
            .drawBehind {
                val r = size.height / 2f
                // Soft outer bloom — multiple ellipses for a lively glow.
                val bloom = glowPulse
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OrangeGlow.copy(alpha = 0.55f * bloom),
                            Orange400.copy(alpha = 0.22f * bloom),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.55f,
                    ),
                    cornerRadius = CornerRadius(r, r),
                    size = Size(size.width, size.height),
                    topLeft = Offset.Zero,
                )
                drawRoundRect(
                    color = Orange400.copy(alpha = 0.35f * bloom),
                    cornerRadius = CornerRadius(r * 1.2f, r * 1.2f),
                    topLeft = Offset(-10f, -8f),
                    size = Size(size.width + 20f, size.height + 22f),
                )
            }
            .shadow(
                elevation = 28.dp,
                shape = shape,
                ambientColor = Orange400.copy(alpha = 0.75f),
                spotColor = OrangeGlow.copy(alpha = 0.95f),
            )
            .clip(shape)
            .background(fill)
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.12f),
                        Color(0xFFFFC48A).copy(alpha = 0.25f),
                    ),
                ),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Top glass shine.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 48f,
                    ),
                ),
        )
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            ),
        )
    }
}

/** Deep-space plate: near-black field, faint stars, orange left + cyan right blooms. */
@Composable
private fun WelcomeSpaceBackdrop(modifier: Modifier = Modifier) {
    val stars = remember {
        val rng = Random(42)
        List(72) {
            StarSpec(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = 0.6f + rng.nextFloat() * 1.8f,
                alpha = 0.08f + rng.nextFloat() * 0.22f,
            )
        }
    }

    Canvas(modifier = modifier.background(Color(0xFF03040A))) {
        // Soft void vignette — keeps center readable, edges deeper black.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0A0D18), Color(0xFF03040A), Color(0xFF010106)),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.maxDimension * 0.75f,
            ),
        )

        // Orange atmospheric glow — left / mid-left.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Orange400.copy(alpha = 0.28f),
                    Orange300.copy(alpha = 0.10f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.18f, size.height * 0.48f),
                radius = size.minDimension * 0.62f,
            ),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 0.18f, size.height * 0.48f),
        )

        // Cyan / blue atmospheric glow — right.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF4EA8FF).copy(alpha = 0.22f),
                    Aqua300.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.84f, size.height * 0.50f),
                radius = size.minDimension * 0.58f,
            ),
            radius = size.minDimension * 0.58f,
            center = Offset(size.width * 0.84f, size.height * 0.50f),
        )

        // Floor reflection wash under the hero.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Orange400.copy(alpha = 0.10f),
                    Color(0xFF4EA8FF).copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.5f, size.height * 0.62f),
                radius = size.minDimension * 0.40f,
            ),
        )

        stars.forEach { star ->
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.radius,
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }
    }
}

private data class StarSpec(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
)

@Composable
private fun LegalFooter(
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val annotated = buildAnnotatedString {
        append(tr("Al continuar, aceptas nuestros ", "By continuing, you agree to our "))
        pushStringAnnotation("terms", TERMS_URL)
        withStyle(SpanStyle(color = Aqua300, textDecoration = TextDecoration.Underline)) {
            append(tr("Términos de Servicio", "Terms of Service"))
        }
        pop()
        append(tr(" y ", " and "))
        pushStringAnnotation("privacy", PRIVACY_URL)
        withStyle(SpanStyle(color = Aqua300, textDecoration = TextDecoration.Underline)) {
            append(tr("Política de Privacidad", "Privacy Policy"))
        }
        pop()
        append(".")
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(
            color = TextMuted.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = { offset ->
            annotated.getStringAnnotations("terms", offset, offset).firstOrNull()?.let {
                onOpenTerms()
                return@ClickableText
            }
            annotated.getStringAnnotations("privacy", offset, offset).firstOrNull()?.let {
                onOpenPrivacy()
            }
        },
    )
}

@Composable
private fun WelcomeHeroAlive() {
    val context = LocalContext.current
    val motionOn = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }

    val loop = rememberInfiniteTransition(label = "welcomeAlive")
    val tick by loop.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(10_000, easing = LinearEasing), RepeatMode.Restart),
        label = "particleTick",
    )
    val bob by loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val tilt by loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "tilt",
    )
    val glow by loop.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    val motion = if (motionOn) 1f else 0f
    val glowAlpha = if (motionOn) glow else 0.6f
    val particleTick = if (motionOn) tick else 0.8f

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val stage = min(maxWidth.value, maxHeight.value)
        val mascotSize = (stage * 0.64f).dp
        val density = LocalDensity.current
        val stagePx = with(density) { stage.dp.toPx() }

        // Split halo bloom: orange left, cyan right — space rim light, no hard ring.
        Canvas(modifier = Modifier.size((stage * 0.96f).dp)) {
            val cx = size.width / 2f
            val cy = size.height * 0.52f
            val r = size.minDimension * 0.46f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Orange400.copy(alpha = 0.34f * glowAlpha),
                        Orange300.copy(alpha = 0.08f * glowAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx - r * 0.55f, cy),
                    radius = r * 0.95f,
                ),
                radius = r * 0.95f,
                center = Offset(cx - r * 0.55f, cy),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF4EA8FF).copy(alpha = 0.30f * glowAlpha),
                        Aqua300.copy(alpha = 0.08f * glowAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx + r * 0.55f, cy),
                    radius = r * 0.95f,
                ),
                radius = r * 0.95f,
                center = Offset(cx + r * 0.55f, cy),
            )
            // Soft ground bounce under Uli.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Orange400.copy(alpha = 0.12f * glowAlpha),
                        Color(0xFF4EA8FF).copy(alpha = 0.08f * glowAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy + r * 0.55f),
                    radius = r * 0.55f,
                ),
            )
        }

        // Orange sparkles — same language as main Uli particles.
        if (motionOn) {
            Canvas(modifier = Modifier.size((stage * 0.88f).dp)) {
                val cx = size.width / 2f
                val cy = size.height * 0.52f
                Sparkles.forEach { p ->
                    val angle = particleTick * p.speed + p.phase
                    val radius = size.minDimension * p.orbit
                    val x = cx + cos(angle) * radius
                    val y = cy + sin(angle * 0.85f) * radius * 0.72f + bob * 1.4f * motion
                    val alpha = 0.28f + 0.42f * glowAlpha
                    if (p.hex) {
                        drawPath(hexPath(Offset(x, y), p.size), Orange400.copy(alpha = alpha))
                    } else {
                        drawCircle(Orange300.copy(alpha = alpha), p.size * 0.5f, Offset(x, y))
                    }
                }
            }
        }

        // Soft-3D distraction bubbles — elliptical drift, depth scale, no track line.
        FloatBubbles.forEach { bubble ->
            val angle = particleTick * bubble.speed + bubble.phase
            val radius = stagePx * bubble.orbit
            val x = cos(angle) * radius
            val y = sin(angle * 0.82f) * radius * bubble.depthAmp + bob * 3.2f * motion
            // Depth: lower on screen / larger = closer.
            val depth = ((y / (stagePx * 0.5f)) + 1f).coerceIn(0f, 2f) / 2f
            val scale = 0.78f + depth * 0.28f
            val alpha = 0.72f + depth * 0.28f
            val sizeDp = bubble.sizeDp.dp

            Image(
                painter = painterResource(bubble.iconRes),
                contentDescription = null,
                modifier = Modifier
                    .zIndex(depth)
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(sizeDp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        rotationZ = if (motionOn) sin(angle) * 4f else 0f
                        shadowElevation = 10f + depth * 12f
                        shape = RoundedCornerShape(16.dp)
                        clip = false
                    },
                contentScale = ContentScale.Fit,
            )
        }

        Image(
            painter = painterResource(R.drawable.uli_welcome_front),
            contentDescription = "Uli",
            modifier = Modifier
                .size(mascotSize)
                .zIndex(0.55f)
                .graphicsLayer {
                    translationY = bob * 5f * motion
                    rotationZ = tilt * 1.6f * motion
                },
            contentScale = ContentScale.Fit,
        )
    }
}

private fun hexPath(center: Offset, radius: Float): Path {
    val path = Path()
    for (i in 0 until 6) {
        val a = (PI / 3.0) * i - PI / 6.0
        val x = center.x + (cos(a) * radius).toFloat()
        val y = center.y + (sin(a) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
