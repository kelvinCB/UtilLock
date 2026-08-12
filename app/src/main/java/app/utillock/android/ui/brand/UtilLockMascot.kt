package app.utillock.android.ui.brand

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.utillock.android.R
import app.utillock.android.ui.theme.Orange300
import app.utillock.android.ui.theme.Orange400
import app.utillock.android.ui.theme.SpaceGrotesk
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

/** Static UtilLock wordmark — Util (white) + Lock (orange). Never animate this. */
@Composable
fun UtilLockWordmark(
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold, fontFamily = SpaceGrotesk)) {
                append("Util")
            }
            withStyle(SpanStyle(color = Orange400, fontWeight = FontWeight.Bold, fontFamily = SpaceGrotesk)) {
                append("Lock")
            }
        },
        style = style,
        modifier = modifier,
    )
}

/**
 * Uli the Hedgehog — transparent soft-3D sprite with layered premium motion.
 * Pass [animated]=false for the header avatar (static, as in the brand mock).
 */
@Composable
fun UliMascot(
    state: UliState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    useHero: Boolean = false,
    animated: Boolean = true,
    useAvatar: Boolean = false,
) {
    val context = LocalContext.current
    val systemAnimations = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) != 0f
    }
    val motionOn = animated && systemAnimations

    val drawable = remember(state, useHero, useAvatar) {
        when {
            useAvatar -> R.drawable.uli_avatar
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
            UliParticle(0.36f, 0.2f, 0.55f, 5f, true),
            UliParticle(0.42f, 1.4f, 0.40f, 3.8f, false),
            UliParticle(0.48f, 2.6f, 0.62f, 6f, true),
            UliParticle(0.40f, 3.9f, 0.48f, 3.4f, false),
            UliParticle(0.50f, 5.1f, 0.35f, 4.8f, true),
            UliParticle(0.44f, 0.9f, 0.58f, 3.6f, false),
        )
    }

    val loop = rememberInfiniteTransition(label = "uliHedgehog")
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
    val sway by loop.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    val glow by loop.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val particleTick by loop.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "particles",
    )

    var blinkCover by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionOn) {
        if (!motionOn) {
            blinkCover = 0f
            return@LaunchedEffect
        }
        while (isActive) {
            delay(3400L + (0..1600).random())
            blinkCover = 1f
            delay(70)
            blinkCover = 0f
            if ((0..3).random() == 0) {
                delay(90)
                blinkCover = 1f
                delay(60)
                blinkCover = 0f
            }
        }
    }

    val motion = if (motionOn) 1f else 0f
    val glowAlpha = if (motionOn) glow else 0.55f

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        if (motionOn && !useAvatar) {
            Canvas(modifier = Modifier.fillMaxSize(0.9f)) {
                val cx = size.width / 2f
                val cy = size.height * 0.58f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Orange400.copy(alpha = 0.20f * glowAlpha),
                            Orange300.copy(alpha = 0.06f * glowAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = size.minDimension * 0.46f,
                    ),
                    radius = size.minDimension * 0.46f,
                    center = Offset(cx, cy),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = bob * 5.2f * motion
                    translationX = sway * 2.0f * motion
                    rotationZ = tilt * 2.2f * motion
                }
                .then(if (useAvatar) Modifier.clip(CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(drawable),
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.96f),
            )

            if (motionOn && !useAvatar && blinkCover > 0.5f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lidH = size.height * 0.035f
                    val top = size.height * 0.36f
                    val lid = Color(0xFF2A1A12).copy(alpha = 0.88f)
                    drawRoundRect(
                        color = lid,
                        topLeft = Offset(size.width * 0.34f, top),
                        size = Size(size.width * 0.14f, lidH),
                        cornerRadius = CornerRadius(lidH, lidH),
                    )
                    drawRoundRect(
                        color = lid,
                        topLeft = Offset(size.width * 0.52f, top),
                        size = Size(size.width * 0.14f, lidH),
                        cornerRadius = CornerRadius(lidH, lidH),
                    )
                }
            }
        }

        if (motionOn && !useAvatar) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height * 0.52f
                particles.forEach { p ->
                    val angle = particleTick * p.speed + p.phase
                    val radius = size.minDimension * p.orbit
                    val x = cx + cos(angle) * radius
                    val y = cy + sin(angle * 0.85f) * radius * 0.7f + bob * 1.2f * motion
                    val alpha = 0.30f + 0.40f * glowAlpha
                    if (p.hex) {
                        drawPath(hexPath(Offset(x, y), p.size), Orange400.copy(alpha = alpha))
                    } else {
                        drawCircle(Orange300.copy(alpha = alpha), p.size * 0.5f, Offset(x, y))
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
        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UtilLockWordmark()
                UliMascot(
                    state = UliState.Idle,
                    useHero = true,
                    modifier = Modifier.wrapContentSize().size(180.dp),
                )
            }
        }
    }
}
