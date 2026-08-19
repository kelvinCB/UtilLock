package app.utillock.android.ui.onboarding

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import app.utillock.android.R
import app.utillock.android.data.SessionRepository
import app.utillock.android.ui.theme.Aqua300
import app.utillock.android.ui.theme.Aqua400
import app.utillock.android.ui.theme.Ink500
import app.utillock.android.ui.theme.Ink700
import app.utillock.android.ui.theme.Ink800
import app.utillock.android.ui.theme.Orange400
import app.utillock.android.ui.theme.Rose300
import app.utillock.android.ui.theme.SpaceGrotesk
import app.utillock.android.ui.theme.TextMuted
import app.utillock.android.ui.theme.TextPrimary
import app.utillock.android.ui.tr
import kotlinx.coroutines.launch

private data class AccountProvider(
    val id: String,
    val label: String,
    val mark: String,
    val markColor: Color,
    val markTextColor: Color,
)

/** Focused account entry: the user's provider choices are the visual priority. */
@Composable
fun CreateAccountScreen(
    sessions: SessionRepository,
    returningMember: Boolean,
    onBack: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onAccountCreated: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linked by sessions.linked.collectAsState()
    var loadingProvider by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val providers = listOf(
        AccountProvider("apple", tr("Continuar con Apple", "Continue with Apple"), "A", Color.White, Ink800),
        AccountProvider("google", tr("Continuar con Google", "Continue with Google"), "G", Color.White, Orange400),
        AccountProvider("facebook", tr("Continuar con Facebook", "Continue with Facebook"), "f", Color(0xFF1877F2), Color.White),
    )
    val unavailableMessage = tr(
        "Esta opción todavía no está habilitada en este entorno.",
        "This option is not enabled in this environment yet.",
    )
    val openProviderError = tr(
        "No se pudo abrir el proveedor. Inténtalo de nuevo.",
        "The provider could not be opened. Try again.",
    )

    LaunchedEffect(linked) {
        if (linked) onAccountCreated()
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        WelcomeSpaceBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .padding(bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    modifier = Modifier.align(Alignment.CenterStart),
                    onClick = onBack,
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = tr("Volver", "Back"), tint = TextPrimary)
                }
                Image(
                    painter = painterResource(R.drawable.imagotipo_shield_lock),
                    contentDescription = "UtilLock",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(160.dp)
                        .height(54.dp),
                )
            }

            Spacer(Modifier.height(54.dp))
            Text(
                if (returningMember) tr("Inicia sesión", "Sign in") else tr("Crea tu cuenta", "Create your account"),
                color = TextPrimary,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp,
                ),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            if (returningMember) {
                Spacer(Modifier.height(30.dp))
            } else {
                Text(
                    tr("Elige una opción para empezar.", "Choose an option to get started."),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(30.dp))
            }

            providers.forEach { provider ->
                AccountProviderButton(
                    provider = provider,
                    loading = loadingProvider == provider.id,
                    enabled = loadingProvider == null,
                    onClick = {
                        scope.launch {
                            loadingProvider = provider.id
                            message = null
                            val url = sessions.providerLinkUrl(provider.id)
                            if (url == null) {
                                message = unavailableMessage
                            } else {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }.onFailure { message = openProviderError }
                            }
                            loadingProvider = null
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            message?.let {
                Text(
                    text = it,
                    color = Rose300,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text(
                tr("También puedes continuar sin cuenta", "You can also continue without an account"),
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink800)
                    .border(1.dp, Ink500, RoundedCornerShape(20.dp))
                    .clickable(enabled = loadingProvider == null, onClick = onContinueWithoutAccount)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr("Continuar sin cuenta", "Continue without account"),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }

        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            LegalFooter(
                onOpenTerms = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, TERMS_URL.toUri()))
                },
                onOpenPrivacy = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_URL.toUri()))
                },
            )
        }
    }
}

@Composable
private fun AccountProviderButton(
    provider: AccountProvider,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Ink800)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(Orange400, Aqua400)),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(provider.markColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = provider.mark,
                color = provider.markTextColor,
                fontSize = if (provider.id == "facebook") 23.sp else 20.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = if (loading) tr("Abriendo…", "Opening…") else provider.label,
            color = if (enabled) Color.White else TextMuted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (!loading) Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Aqua300)
    }
}
