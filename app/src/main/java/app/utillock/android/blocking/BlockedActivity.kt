package app.utillock.android.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.utillock.android.challenge.ChallengeActivity
import app.utillock.android.ui.theme.Navy950
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
    Box(
        modifier = Modifier.fillMaxSize().background(Navy950).padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)).padding(26.dp),
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(28.dp))
            Text(tr("Bloqueo activo", "Block active"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                blockedTarget,
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                tr("Si de verdad necesitas entrar, resuelve un ejercicio y toma una foto de tu respuesta.", "If you truly need access, solve an exercise and take a photo of your answer."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onChallenge, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(tr("Solicitar pausa", "Request a pause"))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            ) {
                Text(tr("Volver al inicio", "Go home"))
            }
        }
    }
}
