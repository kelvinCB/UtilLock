package app.utillock.android.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.utillock.android.challenge.ChallengeActivity
import app.utillock.android.ui.components.PremiumButton
import app.utillock.android.ui.theme.Ink950
import app.utillock.android.ui.theme.UtilLockGradients
import app.utillock.android.ui.theme.UtilLockTheme
import app.utillock.android.ui.tr

class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            UtilLockTheme {
                BlockedScreen(
                    blockedTarget = intent.getStringExtra(EXTRA_URL)
                        ?: readableAppName(intent.getStringExtra(EXTRA_PACKAGE).orEmpty()),
                    onChallenge = {
                        startActivity(Intent(this, ChallengeActivity::class.java))
                        finish()
                    },
                    onLeave = ::goHome,
                )
            }
        }
    }

    private fun readableAppName(packageName: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
        const val EXTRA_URL = "blocked_url"
    }
}

@Composable
private fun BlockedScreen(blockedTarget: String, onChallenge: () -> Unit, onLeave: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "lockPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val glow by infinite.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "pulseGlow",
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Ink950).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = glow)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(UtilLockGradients.dangerGlow),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(tr("Bloqueo activo", "Block active"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                blockedTarget,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                tr("Si de verdad necesitas entrar, resuelve un ejercicio y toma una foto de tu respuesta.", "If you truly need access, solve an exercise and take a photo of your answer."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            PremiumButton(
                text = tr("Solicitar pausa", "Request a pause"),
                onClick = onChallenge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            // Rendered as a plain tap target rather than an outlined button — keeps the
            // primary action above visually dominant on this screen.
            Text(
                tr("Volver al inicio", "Go home"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onLeave)
                    .padding(vertical = 16.dp),
            )
        }
    }
}
