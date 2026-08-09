package app.utillock.android.ui.brand

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.utillock.android.R
import app.utillock.android.ui.theme.Orange300
import app.utillock.android.ui.theme.Orange400
import app.utillock.android.ui.theme.UtilLockTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class UliState {
    Idle,
    Protected,
    Blocking,
    Thinking,
    Success,
    Paused,
}

private data class UliParticle(
    val orbit: Float,
    val phase: Float,
    val speed: Float,
    val size: Float,
    val hex: Boolean,
)

/**
 * Premium Uli: transparent soft-3D sprite + layered motion (bob, tilt, glow, particles, blink).
 * No square backdrop — only the character floats over the host surface.
 */
@Composable
fun UliMascot(
    state: UliState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    useHero: Boolean = false,
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }

    val drawable = remember(state, useHero) {
        when {
            useHero && state == UliState.Idle -> R.drawable.uli_hero
            state == UliState.Protected -> R.drawable.uli_protected
            state == UliState.Blocking -> R.drawable.uli_blocking
            state == UliState.Thinking -> R.drawable.uli_thinking
            state == UliState.Success -> R.drawable.uli_success
            state == UliState.Paused -> R.drawable.uli_paused
            else -> R.drawable.uli_idle
        }
    }

    val particles = remember {
        listOf(
            UliParticle(0.34f, 0.1f, 0.55f, 5.5f, true),
            UliParticle(0.40f, 1.2f, 0.42f, 4.0f, false),
            UliParticle(0.46f, 2.4f, 0.68f, 6.5f, true),
            UliParticle(0.38f, 3.7f, 0.50f, 3.5f, false),
            UliParticle(0.50f, 4.8f, 0.36f, 5.0f, true),
            UliParticle(0.42f, 5.5f, 0.60f, 3.8f, false),
            UliParticle(0.48f, 0.8f, 0.47f, 4.6f, true),
            UliParticle(0.36f, 2.9f, 0.58f, 3.2f, false),
        )
    }

    val loop = rememberInfiniteTransition(label = "uliLife")
    val bob by loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "uliBob",
    )
    val tilt by loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "uliTilt",
    )
    val sway by loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "uliSway",
    )
    val glow by loop.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "uliGlow",
    )
    val particleTick by loop.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "uliParticles",
    )

    var blinkCover by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            blinkCover = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            delay(3200L + (0..1800).random())
            // Quick blink: down then up.
            blinkCover = 1f
            delay(70)
            blinkCover = 0f
            delay(90)
            // Occasional double-blink.
            if ((0..3).random() == 0) {
                blinkCover = 1f
                delay(60)
                blinkCover = 0f
            }
        }
    }

    val motion = if (animationsEnabled) 1f else 0f
    val bobPx = bob * 5.5f * motion
    val tiltDeg = tilt * 2.4f * motion
    val swayPx = sway * 2.2f * motion
    val glowAlpha = if (animationsEnabled) glow else 0.55f

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        // Soft ambient glow behind the character (not a square card).
        Canvas(modifier = Modifier.fillMaxSize(0.92f)) {
            val cx = size.width / 2f
            val cy = size.height * 0.56f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Orange400.copy(alpha = 0.22f * glowAlpha),
                        Orange300.copy(alpha = 0.08f * glowAlpha),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.48f,
                ),
                radius = size.minDimension * 0.48f,
                center = Offset(cx, cy),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = bobPx
                    translationX = swayPx
                    rotationZ = tiltDeg
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = drawable,
                transitionSpec = {
                    fadeIn(tween(280)) togetherWith fadeOut(tween(220))
                },
                label = "uliState",
                modifier = Modifier.fillMaxSize(0.94f),
            ) { resId ->
                Image(
                    painter = painterResource(resId),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Chest-core pulse — sits over the lock area without scaling the whole sprite.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.55f + 0.45f * glowAlpha },
            ) {
                val cx = size.width * 0.50f
                val cy = size.height * 0.58f
                val r = size.minDimension * (0.055f + 0.012f * glowAlpha)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Orange400.copy(alpha = 0.75f),
                            Orange300.copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = r * 2.8f,
                    ),
                    radius = r * 2.8f,
                    center = Offset(cx, cy),
                )
            }

            // Occasional blink veil over the eyes.
            if (blinkCover > 0.5f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lidH = size.height * 0.045f
                    val top = size.height * 0.385f
                    val lidColor = Color(0xFF1B2140).copy(alpha = 0.9f)
                    drawRoundRect(
                        color = lidColor,
                        topLeft = Offset(size.width * 0.35f, top),
                        size = Size(size.width * 0.18f, lidH),
                        cornerRadius = CornerRadius(lidH, lidH),
                    )
                    drawRoundRect(
                        color = lidColor,
                        topLeft = Offset(size.width * 0.57f, top),
                        size = Size(size.width * 0.18f, lidH),
                        cornerRadius = CornerRadius(lidH, lidH),
                    )
                }
            }
        }

        // Independent floating hex / ember particles around Uli.
        if (animationsEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.52f
                particles.forEach { p ->
                    val angle = particleTick * p.speed + p.phase
                    val radius = size.minDimension * p.orbit
                    val x = cx + cos(angle) * radius
                    val y = cy + sin(angle * 0.85f) * radius * 0.72f + bobPx * 0.3f
                    val alpha = 0.35f + 0.45f * glowAlpha
                    if (p.hex) {
                        val path = hexPath(Offset(x, y), p.size)
                        drawPath(path, Orange400.copy(alpha = alpha))
                    } else {
                        drawCircle(
                            color = Orange300.copy(alpha = alpha),
                            radius = p.size * 0.55f,
                            center = Offset(x, y),
                        )
                    }
                }
            }
        }
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

@Preview(showBackground = true, backgroundColor = 0xFF05060E)
@Composable
private fun UliMascotPreview() {
    UtilLockTheme {
        Box(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            UliMascot(
                state = UliState.Idle,
                useHero = true,
                modifier = Modifier.wrapContentSize().size(220.dp),
            )
        }
    }
}
