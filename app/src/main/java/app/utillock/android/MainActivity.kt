package app.utillock.android

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material.icons.rounded.SavedSearch
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.utillock.android.billing.BillingRepository
import app.utillock.android.blocking.UsageMonitorService
import app.utillock.android.data.ProtectionRepository
import app.utillock.android.data.SessionRepository
import app.utillock.android.data.BackendClient
import app.utillock.android.filter.VpnController
import app.utillock.android.model.BlockSchedule
import app.utillock.android.model.ScheduleEvaluator
import app.utillock.android.model.parseMinute
import app.utillock.android.ui.brand.UliMascot
import app.utillock.android.ui.brand.UliState
import app.utillock.android.ui.brand.UtilLockWordmark
import app.utillock.android.ui.components.CountdownRing
import app.utillock.android.ui.components.EmptyState
import app.utillock.android.ui.components.FeatureRow
import app.utillock.android.ui.components.GlowIconBadge
import app.utillock.android.ui.components.GradientCard
import app.utillock.android.ui.components.PremiumButton
import app.utillock.android.ui.components.SectionHeader
import app.utillock.android.ui.onboarding.WelcomeScreen
import app.utillock.android.ui.theme.Aqua400
import app.utillock.android.ui.theme.Ink800
import app.utillock.android.ui.theme.Navy700
import app.utillock.android.ui.theme.Orange300
import app.utillock.android.ui.theme.Orange400
import app.utillock.android.ui.theme.OrangeGlow
import app.utillock.android.ui.theme.UtilLockGradients
import app.utillock.android.ui.theme.UtilLockTheme
import app.utillock.android.ui.tr
import app.utillock.android.ui.currentLanguage
import app.utillock.android.ui.setLanguage
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.SideEffect

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as UtilLockApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the system splash until our Compose brand splash is on screen,
        // so the default icon frame never appears as a separate beat.
        val keepSystemSplash = AtomicBoolean(true)
        splashScreen.setKeepOnScreenCondition { keepSystemSplash.get() }

        lifecycleScope.launch { container.sessionRepository.importAuthRedirect(intent.data) }
        container.billingRepository.connect()
        lifecycleScope.launch {
            if (container.protectionRepository.awaitLoaded().usageMonitorEnabled && hasUsageAccess(this@MainActivity)) {
                UsageMonitorService.start(this@MainActivity)
            }
        }
        setContent {
            UtilLockTheme {
                val protection = container.protectionRepository
                val protectionState by protection.state.collectAsState()
                var showBrandSplash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    protection.awaitLoaded()
                    delay(900L)
                    showBrandSplash = false
                }

                when {
                    showBrandSplash -> {
                        SideEffect { keepSystemSplash.set(false) }
                        BrandSplashScreen()
                    }
                    !protectionState.onboardingComplete -> WelcomeScreen(
                        onStart = { protection.completeOnboarding() },
                        onAlreadyMember = { protection.completeOnboarding() },
                    )
                    else -> UtilLockApp(
                        protection,
                        container.sessionRepository,
                        container.backendClient,
                        container.billingRepository,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { container.sessionRepository.importAuthRedirect(intent.data) }
    }
}

@Composable
private fun BrandSplashScreen() {
    Image(
        painter = painterResource(R.drawable.splash_shield),
        contentDescription = "UtilLock",
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}

private enum class AppTab(val es: String, val en: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    BLOCK("Bloqueo", "Block", Icons.Rounded.Shield),
    PROTECTION("Protección", "Protection", Icons.Rounded.VerifiedUser),
    PROFILE("Perfil", "Profile", Icons.Rounded.Person),
}

@Composable
private fun UtilLockApp(
    repository: ProtectionRepository,
    sessions: SessionRepository,
    backend: BackendClient,
    billing: BillingRepository,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val state by repository.state.collectAsState()
    val billingState by billing.state.collectAsState()
    var tab by remember { mutableStateOf(AppTab.BLOCK) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(billingState.premium) {
        if (billingState.premium != state.premium) repository.setPremium(billingState.premium)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PremiumNavBar(selected = tab, onSelect = { tab = it })
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tabContent",
        ) { current ->
            when (current) {
                AppTab.BLOCK -> DashboardScreen(repository, now, Modifier.fillMaxSize())
                AppTab.PROTECTION -> ProtectionScreen(repository, Modifier.fillMaxSize())
                AppTab.PROFILE -> ProfileScreen(
                    premium = state.premium,
                    repository = repository,
                    sessions = sessions,
                    backend = backend,
                    billing = billing,
                    activity = activity,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun PremiumNavBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppTab.entries.forEach { item ->
            val isSelected = item == selected
            val background = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
            val tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(background)
                    .clickable { onSelect(item) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icon, contentDescription = null, tint = tint)
                Spacer(Modifier.height(4.dp))
                Text(tr(item.es, item.en), color = tint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private const val DEFAULT_QUICK_BLOCK_MINUTES = 60

@Composable
private fun DashboardScreen(repository: ProtectionRepository, now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val active = ScheduleEvaluator.activeProtection(state, LocalDateTime.now(), now)
    var showSchedule by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showQuickBlockSetup by remember { mutableStateOf(false) }
    val quickBlockActive = state.isQuickBlockActive(now)
    val hasBlockingConfiguration = state.blockedPackages.isNotEmpty() || state.blockedDomains.isNotEmpty()
    val protectionReady = hasBlockingConfiguration

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UliMascot(
                    state = UliState.Idle,
                    useAvatar = true,
                    animated = false,
                    modifier = Modifier.size(48.dp),
                    contentDescription = tr("Uli el erizo", "Uli the hedgehog"),
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    UtilLockWordmark(style = MaterialTheme.typography.titleLarge)
                    Text(
                        tr("Tu atención bajo cuidado", "Your attention, protected"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        item {
            HeroCard(
                frontBlockActive = quickBlockActive,
                onToggleFrontBlock = {
                    if (quickBlockActive) repository.stopQuickBlock()
                    else repository.setQuickBlock(DEFAULT_QUICK_BLOCK_MINUTES)
                },
                onOpenSetup = { showQuickBlockSetup = true },
            )
        }
        item {
            ProtectionReadyPill(ready = protectionReady || quickBlockActive || active.active)
        }
        item {
            SectionHeader(
                title = tr("Programaciones", "Schedules"),
                subtitle = tr("Automatiza tu bloqueo cada semana", "Automate blocking every week"),
                trailing = {
                    GlowIconBadge(
                        icon = Icons.Rounded.Add,
                        size = 40.dp,
                        contentDescription = tr("Crear programación", "Create schedule"),
                        modifier = Modifier.clickable { showSchedule = true },
                    )
                },
            )
        }
        if (state.schedules.isEmpty()) {
            item { EmptyState(Icons.Rounded.LockClock, tr("Aún no hay horarios. Toca + para crear el primero.", "No schedules yet. Tap + to create your first one.")) }
        }
        items(state.schedules, key = { it.id }) { schedule ->
            ScheduleCard(
                schedule = schedule,
                onToggle = { repository.setScheduleEnabled(schedule.id, it) },
                onDelete = { repository.removeSchedule(schedule.id) },
            )
        }
    }

    if (showSchedule) ScheduleDialog(repository, onDismiss = { showSchedule = false })
    if (showQuickBlockSetup) QuickBlockSetupScreen(onBack = { showQuickBlockSetup = false })
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(tr("Activa el permiso de bloqueo", "Enable blocking access")) },
            text = {
                Text(
                    tr(
                        "Para bloquear aplicaciones debes activar Accesibilidad en Ajustes. Android no concede este permiso automáticamente. Después vuelve a UtilLock y pulsa Iniciar.",
                        "To block apps, enable Accessibility in Settings. Android does not grant this access automatically. Return to UtilLock and tap Start.",
                    ),
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text(tr("Abrir Ajustes", "Open Settings")) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPermissionDialog = false }) { Text(tr("Cancelar", "Cancel")) }
            },
        )
    }
}

@Composable
private fun ProtectionReadyPill(ready: Boolean) {
    val shape = RoundedCornerShape(50)
    val mint = Color(0xFF5EF0C8)
    val mintSoft = Color(0xFF3AD7B0)
    val pillBg = if (ready) Color(0xFF0D1B1E) else Ink800
    val border = if (ready) {
        Brush.horizontalGradient(
            listOf(
                mint.copy(alpha = 0.45f),
                mintSoft.copy(alpha = 0.22f),
                mint.copy(alpha = 0.38f),
            ),
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.06f)),
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = if (ready) 10.dp else 0.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = mint.copy(alpha = 0.25f),
                    spotColor = mint.copy(alpha = 0.35f),
                )
                .clip(shape)
                .background(pillBg)
                .border(width = 1.dp, brush = border, shape = shape)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (ready) {
                Image(
                    painter = painterResource(R.drawable.ic_protection_ready),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (ready) tr("Protección lista", "Protection ready")
                else tr("Configura protección para empezar", "Set up protection to start"),
                color = if (ready) mint else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun HeroCard(
    frontBlockActive: Boolean,
    onToggleFrontBlock: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Navy700)
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Bloqueo rápido", "Quick block"),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (frontBlockActive) {
                            tr("Bloqueo en curso. Pulsa Detener cuando termines.", "Block running. Tap Stop when you’re done.")
                        } else {
                            tr("Yo cuido tu foco.", "I’ll guard your focus.")
                        },
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                UliMascot(
                    state = if (frontBlockActive) UliState.Protected else UliState.Idle,
                    useHero = true,
                    animated = true,
                    modifier = Modifier.size(118.dp),
                    contentDescription = tr("Uli el erizo cuida tu foco", "Uli the hedgehog guards your focus"),
                )
            }
            Spacer(Modifier.height(20.dp))
            StartStopCta(
                active = frontBlockActive,
                onClick = onToggleFrontBlock,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ModeChip(
                    iconRes = R.drawable.ic_mode_clock,
                    label = tr("Temporizado", "Timed"),
                    onClick = onOpenSetup,
                    modifier = Modifier.weight(1f),
                )
                ModeChip(
                    iconRes = R.drawable.ic_mode_tomato,
                    label = "Pomodoro",
                    onClick = onOpenSetup,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StartStopCta(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val fill = if (active) {
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.10f)),
        )
    } else {
        Brush.horizontalGradient(listOf(Orange400, OrangeGlow, Orange300))
    }
    val stroke = if (active) {
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.12f)),
        )
    } else {
        Brush.horizontalGradient(
            listOf(Color.White.copy(alpha = 0.65f), OrangeGlow, Color.White.copy(alpha = 0.28f)),
        )
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Soft outer glow bloom (design “halo”).
        if (!active) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(
                        elevation = 22.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = Orange400.copy(alpha = 0.55f),
                        spotColor = OrangeGlow.copy(alpha = 0.9f),
                    )
                    .background(Orange400.copy(alpha = 0.22f), shape),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = if (active) 6.dp else 16.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = if (active) Color.Black.copy(alpha = 0.25f) else Orange400.copy(alpha = 0.65f),
                    spotColor = if (active) Color.Black.copy(alpha = 0.35f) else OrangeGlow.copy(alpha = 0.95f),
                )
                .clip(shape)
                .background(fill)
                .border(width = 1.6.dp, brush = stroke, shape = shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.25f),
                        spotColor = Color.Black.copy(alpha = 0.3f),
                    )
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (active) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Orange400,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (active) tr("Detener bloqueo", "Stop block") else tr("Iniciar", "Start"),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun ModeChip(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    // Match design: deep navy chip with subtle luminous rim (not washed gray).
    val chipFill = Color(0xFF151B31)
    val chipBorder = Brush.horizontalGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.10f),
            Color.White.copy(alpha = 0.18f),
        ),
    )
    Row(
        modifier = modifier
            .height(58.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(chipFill)
            .border(width = 1.dp, brush = chipBorder, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuickBlockSetupScreen(onBack: () -> Unit) {
    var blockAdultContent by remember { mutableStateOf(false) }
    var blockPurchases by remember { mutableStateOf(false) }
    var blockUnsupportedBrowsers by remember { mutableStateOf(false) }
    var launchOnStart by remember { mutableStateOf(true) }
    var showNotifications by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showBlockingList by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
                }
            }
            item {
                Text(
                    tr("Configura tu bloqueo rápido", "Configure your quick block"),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    tr(
                        "Estos ajustes se aplicarán cada vez que inicies un bloqueo rápido, temporizado o Pomodoro.",
                        "These settings apply whenever you start a quick, timed, or Pomodoro block.",
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Bloqueo", "Blocking"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            tr("Selecciona lo que quieres proteger.", "Choose what you want to protect."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        tr("Lista de bloqueos", "Block list"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    SetupDestinationRow(Icons.Rounded.AppBlocking, tr("Aplicaciones", "Apps"), "0") { selectedCategory = "apps" }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    SetupDestinationRow(Icons.Rounded.Language, tr("Sitios web", "Websites"), "0") { selectedCategory = "websites" }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    SetupDestinationRow(Icons.Rounded.SavedSearch, tr("Palabras clave", "Keywords"), "0") { selectedCategory = "keywords" }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr("Copiar de otra configuración", "Copy from another setup"), fontWeight = FontWeight.Bold)
                }
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item {
                Text(tr("Protecciones opcionales", "Optional protections"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item {
                SetupToggleCard(
                    icon = Icons.Rounded.Security,
                    title = tr("Bloqueo de contenido adulto", "Adult content blocking"),
                    description = tr("Protege tus navegadores con un filtro DNS familiar.", "Protect your browsers with a family DNS filter."),
                    checked = blockAdultContent,
                    onCheckedChange = { blockAdultContent = it },
                )
            }
            item {
                SetupToggleCard(
                    icon = Icons.Rounded.Bolt,
                    title = tr("Bloquear compras integradas", "Block in-app purchases"),
                    description = tr("Un freno visual para compras no deseadas.", "A visual guardrail for unwanted purchases."),
                    checked = blockPurchases,
                    onCheckedChange = { blockPurchases = it },
                )
            }
            item {
                SetupToggleCard(
                    icon = Icons.Rounded.Language,
                    title = tr("Navegadores no compatibles", "Unsupported browsers"),
                    description = tr("Bloquea el navegador cuando no se puede leer su página activa.", "Block the browser when its active page cannot be read."),
                    checked = blockUnsupportedBrowsers,
                    onCheckedChange = { blockUnsupportedBrowsers = it },
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
            item {
                Text(tr("Comportamiento", "Behavior"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    SetupPreferenceRow(tr("Inicio", "Start"), launchOnStart) { launchOnStart = it }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    SetupPreferenceRow(tr("Notificaciones", "Notifications"), showNotifications) { showNotifications = it }
                }
            }
        }
        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text(tr("Guardar configuración", "Save setup"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        selectedCategory?.let { category ->
            BlockingCategoryIntro(
                category = category,
                onBack = { selectedCategory = null },
                onAllow = {
                    selectedCategory = null
                    showBlockingList = true
                },
            )
        }
        if (showBlockingList) {
            BlockingListScreen(
                onBack = { showBlockingList = false },
                onSave = { showBlockingList = false },
            )
        }
    }
}

@Composable
private fun SetupDestinationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(count, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(10.dp))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun BlockingCategoryIntro(category: String, onBack: () -> Unit, onAllow: () -> Unit) {
    val title = when (category) {
        "websites" -> tr("Un espacio más tranquilo empieza aquí", "A calmer space starts here")
        "keywords" -> tr("Tus palabras también marcan el ritmo", "Your words can set the pace")
        else -> tr("Dale a tus apps un límite claro", "Give your apps a clear boundary")
    }
    val description = when (category) {
        "websites" -> tr(
            "Crea una pausa consciente para las páginas que más interrumpen tu atención.",
            "Create a mindful pause for the websites that interrupt your attention most.",
        )
        "keywords" -> tr(
            "Añade palabras que quieras mantener fuera de tu foco durante este bloqueo.",
            "Add words you want to keep outside your focus during this block.",
        )
        else -> tr(
            "Selecciona las aplicaciones que quieres dejar fuera para volver a lo importante.",
            "Choose the apps you want to keep out so you can return to what matters.",
        )
    }
    val privacyNote = tr(
        "Tu selección se queda en el dispositivo. UtilLock solo la usa para aplicar tus reglas de bloqueo.",
        "Your selection stays on this device. UtilLock only uses it to apply your blocking rules.",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(tr("Personaliza tu bloqueo", "Customize your block"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Image(
                    painter = painterResource(R.drawable.quick_block_categories),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(278.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop,
                )
            }
            item {
                Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            item {
                Text(description, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlowIconBadge(Icons.Rounded.VerifiedUser, size = 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tr("Privacidad primero", "Privacy first"), fontWeight = FontWeight.Bold)
                        Text(privacyNote, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text(
                    tr("Puedes cambiar esta decisión cuando quieras desde tu lista de bloqueo.", "You can change this choice anytime from your block list."),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                Text(tr("Permitir y continuar", "Allow and continue"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                Text(tr("Ahora no", "Not now"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BlockingListScreen(onBack: () -> Unit, onSave: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var expandedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    val categories = listOf(
        Triple("social", Icons.Rounded.Workspaces, tr("Redes sociales", "Social networks")),
        Triple("games", Icons.Rounded.Bolt, tr("Juegos", "Games")),
        Triple("entertainment", Icons.Rounded.Wifi, tr("Entretenimiento", "Entertainment")),
        Triple("creativity", Icons.Rounded.SavedSearch, tr("Creatividad", "Creativity")),
        Triple("education", Icons.Rounded.VerifiedUser, tr("Educación", "Education")),
        Triple("health", Icons.Rounded.Security, tr("Salud y bienestar", "Health & wellness")),
        Triple("productivity", Icons.Rounded.PlayArrow, tr("Productividad", "Productivity")),
        Triple("news", Icons.Rounded.LockClock, tr("Noticias y libros", "News & books")),
        Triple("shopping", Icons.Rounded.Language, tr("Compras y comida", "Shopping & food")),
        Triple("travel", Icons.Rounded.Person, tr("Viajes", "Travel")),
        Triple("utilities", Icons.Rounded.AppBlocking, tr("Utilidades", "Utilities")),
        Triple("other", Icons.Rounded.Workspaces, tr("Otros", "Other")),
    )
    val appsByCategory = mapOf(
        "social" to listOf("Instagram", "TikTok", "WhatsApp", "Snapchat"),
        "games" to listOf("Astro Builder", "Balls Bounce!", "Bee Factory", "Clash of Clans", "Clash Royale", "coin_toss", "Contexto"),
        "entertainment" to listOf("YouTube", "Spotify", "Netflix"),
        "creativity" to listOf("Canva", "Pinterest", "CapCut"),
        "education" to listOf("Duolingo", "Khan Academy", "Coursera"),
        "health" to listOf("Google Fit", "Meditação", "Sleep Cycle"),
        "productivity" to listOf("Notion", "Google Drive", "Calendar"),
        "news" to listOf("Google News", "Kindle", "The New York Times"),
        "shopping" to listOf("Amazon", "AliExpress", "Mercado Libre"),
        "travel" to listOf("Google Maps", "Booking", "Uber"),
        "utilities" to listOf("Calculadora", "Files", "Scanner"),
        "other" to listOf("Aplicación sin categoría"),
    )
    val tabs = listOf(tr("Aplicaciones", "Apps"), tr("Webs", "Websites"), tr("Palabras clave", "Keywords"))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = tr("Volver", "Back"))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(tr("Lista de bloqueos", "Block list"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, tab ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                tab,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(if (selectedTab == index) MaterialTheme.colorScheme.primary else Color.Transparent),
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (selectedTab == 0) {
                item {
                    Text(
                        tr("Categorías disponibles en tu dispositivo", "Categories available on your device"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                categories.forEach { (id, icon, label) ->
                    item(key = "category-$id") {
                        CategorySelectionRow(
                            icon = icon,
                            label = label,
                            expanded = id in expandedCategories,
                            selected = id in selectedCategories,
                            onExpand = {
                                expandedCategories = if (id in expandedCategories) expandedCategories - id else expandedCategories + id
                            },
                            onSelectedChange = { checked ->
                                selectedCategories = if (checked) selectedCategories + id else selectedCategories - id
                            },
                        )
                    }
                    if (id in expandedCategories) {
                        items(appsByCategory[id].orEmpty(), key = { app -> "app-$id-$app" }) { app ->
                            BlockingAppRow(
                                appName = app,
                                selected = app in selectedApps,
                                onSelectedChange = { checked ->
                                    selectedApps = if (checked) selectedApps + app else selectedApps - app
                                },
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GlowIconBadge(if (selectedTab == 1) Icons.Rounded.Language else Icons.Rounded.SavedSearch, size = 56.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (selectedTab == 1) tr("Tus webs aparecerán aquí", "Your websites will appear here") else tr("Tus palabras clave aparecerán aquí", "Your keywords will appear here"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (selectedTab == 1) tr("Añade direcciones web o dominios cuando quieras personalizar este bloqueo.", "Add websites or domains whenever you want to customize this block.") else tr("Añade palabras para mantener ciertas búsquedas fuera de tu foco.", "Add words to keep certain searches outside your focus."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Button(
            onClick = onSave,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            shape = RoundedCornerShape(50),
        ) {
            Text(tr("Guardar selección", "Save selection"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategorySelectionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    onExpand: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onExpand)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (expanded) "⌃" else "⌄", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(12.dp))
        GlowIconBadge(icon, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Checkbox(checked = selected, onCheckedChange = onSelectedChange)
    }
}

@Composable
private fun BlockingAppRow(appName: String, selected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onSelectedChange(!selected) }
            .padding(start = 82.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowIconBadge(Icons.Rounded.AppBlocking, size = 42.dp, brush = UtilLockGradients.heroSoft)
        Spacer(Modifier.width(14.dp))
        Text(appName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Checkbox(checked = selected, onCheckedChange = onSelectedChange)
    }
}

@Composable
private fun SetupToggleCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        GlowIconBadge(icon, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SetupPreferenceRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StatusPillOnGradient(active: Boolean, blockedApps: Int, blockedSites: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (active) Color.White else Color.White.copy(alpha = 0.5f)))
        Spacer(Modifier.width(6.dp))
        Text(
            if (active) tr("ACTIVO", "ACTIVE") else "$blockedApps apps · $blockedSites ${tr("sitios", "sites")}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun formatCountdown(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun ScheduleCard(schedule: BlockSchedule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    GradientCard(brush = UtilLockGradients.heroSoft, shape = MaterialTheme.shapes.large) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIconBadge(Icons.Rounded.LockClock, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(schedule.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatMinute(schedule.startMinute)}–${formatMinute(schedule.endMinute)} · ${schedule.days.size} ${tr("días", "days")}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${schedule.packages.size} apps · ${schedule.domains.size} ${tr("sitios", "sites")}${if (schedule.blockAdultContent) " · +18" else ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            tr("Eliminar", "Delete"),
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onDelete),
        )
    }
}

@Composable
private fun ScheduleDialog(repository: ProtectionRepository, onDismiss: () -> Unit) {
    val state by repository.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("17:00") }
    var adult by remember { mutableStateOf(state.adultFilterEnabled) }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    val startMinute = parseMinute(start)
    val endMinute = parseMinute(end)
    val valid = startMinute != null && endMinute != null && name.isNotBlank() && days.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nueva programación", "New schedule"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Nombre", "Name")) }, singleLine = true, shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text(tr("Inicio", "Start")) }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium)
                    OutlinedTextField(end, { end = it }, label = { Text(tr("Fin", "End")) }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.medium)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tr("L,M,X,J,V,S,D", "M,T,W,T,F,S,S").split(',').forEachIndexed { index, label ->
                        val isSelected = index + 1 in days
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { days = if (isSelected) days - (index + 1) else days + (index + 1) }
                                .size(34.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Bloquear contenido +18", "Block adult content"), modifier = Modifier.weight(1f))
                    Switch(checked = adult, onCheckedChange = { adult = it })
                }
                Text(tr("Usará las apps y sitios seleccionados actualmente.", "Uses the currently selected apps and sites."), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val safeStartMinute = startMinute ?: return@Button
                    val safeEndMinute = endMinute ?: return@Button
                    repository.addSchedule(
                        BlockSchedule(
                            name = name.trim(),
                            startMinute = safeStartMinute,
                            endMinute = safeEndMinute,
                            days = days,
                            packages = state.blockedPackages,
                            domains = state.blockedDomains,
                            blockAdultContent = adult,
                        ),
                    )
                    onDismiss()
                },
            ) { Text(tr("Crear", "Create")) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(tr("Cancelar", "Cancel")) } },
    )
}

@Composable
private fun ProtectionScreen(repository: ProtectionRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var disclosure by remember { mutableStateOf<String?>(null) }
    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            VpnController.start(context)
            repository.setDnsVpn(true)
        }
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val accessibility = remember(refresh) { isAccessibilityEnabled(context) }
    val usage = remember(refresh) { hasUsageAccess(context) }
    val notificationsReady = Build.VERSION.SDK_INT < 33 || NotificationManagerCompat.from(context).areNotificationsEnabled()
    val blockingReady = accessibility || (usage && state.usageMonitorEnabled)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(tr("Protección", "Protection"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(tr("Android exige que actives cada capacidad de forma explícita.", "Android requires you to enable each capability explicitly."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        item {
            GradientCard(
                brush = if (blockingReady) UtilLockGradients.hero else UtilLockGradients.heroSoft,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UliMascot(
                        state = if (blockingReady) UliState.Protected else UliState.Idle,
                        modifier = Modifier.size(88.dp),
                        contentDescription = if (blockingReady) {
                            tr("Uli confirma que el bloqueo está listo", "Uli confirms blocking is ready")
                        } else {
                            tr("Uli espera que completes la protección", "Uli is waiting for protection setup")
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (blockingReady) tr("Tu foco está protegido.", "Your focus is protected.")
                            else tr("Completemos la protección.", "Let’s finish protection."),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (blockingReady) {
                                tr("Uli está listo para detener las distracciones.", "Uli is ready to stop distractions.")
                            } else {
                                tr("Activa Accesibilidad o el respaldo de uso.", "Enable Accessibility or Usage Access backup.")
                            },
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            FeatureRow(
                icon = Icons.Rounded.SettingsAccessibility,
                title = tr("Accesibilidad", "Accessibility"),
                description = tr("Detecta las apps elegidas y la URL visible en navegadores compatibles.", "Detects selected apps and the visible URL in supported browsers."),
                enabled = accessibility,
                onClick = { disclosure = "accessibility" },
            )
        }
        item {
            FeatureRow(
                icon = Icons.Rounded.SavedSearch,
                title = tr("Acceso de uso (respaldo)", "Usage access (backup)"),
                description = tr("Permite comprobar qué app está en primer plano cuando el fabricante limita Accesibilidad.", "Checks the foreground app when the device limits Accessibility."),
                enabled = usage && state.usageMonitorEnabled,
                onClick = {
                    if (!usage) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } else if (state.usageMonitorEnabled) {
                        UsageMonitorService.stop(context)
                        repository.setUsageMonitor(false)
                    } else {
                        UsageMonitorService.start(context)
                        repository.setUsageMonitor(true)
                    }
                    refresh++
                },
            )
        }
        item {
            FeatureRow(
                icon = Icons.Rounded.Wifi,
                title = tr("Filtro DNS +18", "Adult DNS filter"),
                description = tr("Crea una VPN local de solo DNS. No es compatible con otra VPN simultánea.", "Creates a local DNS-only VPN. It cannot run alongside another VPN."),
                enabled = state.dnsVpnEnabled,
                onClick = {
                    if (state.dnsVpnEnabled) {
                        VpnController.stop(context)
                        repository.setDnsVpn(false)
                    } else disclosure = "vpn"
                },
            )
        }
        if (!notificationsReady) {
            item {
                FeatureRow(
                    icon = Icons.Rounded.Bolt,
                    title = tr("Notificaciones", "Notifications"),
                    description = tr("Necesaria para mostrar que el filtro DNS permanece activo.", "Required to show that DNS filtering remains active."),
                    enabled = false,
                    onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
        }
        item {
            OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) {
                Text(tr("Actualizar estado", "Refresh status"))
            }
        }
    }

    if (disclosure != null) {
        val isVpn = disclosure == "vpn"
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text(if (isVpn) tr("Antes de activar la VPN local", "Before enabling the local VPN") else tr("Antes de activar Accesibilidad", "Before enabling Accessibility"), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isVpn) {
                        tr("UtilLock enviará únicamente consultas DNS a Cloudflare para bloquear dominios adultos o elegidos. No inspecciona el contenido de tus conexiones. Android mostrará el icono de VPN.", "UtilLock sends only DNS queries to Cloudflare to block adult or selected domains. It does not inspect connection content. Android will show the VPN icon.")
                    } else {
                        tr("UtilLock leerá el nombre de la app en pantalla y, en navegadores compatibles, el texto de la barra de direcciones. Se usa solo para aplicar tus reglas; no se almacena ni se envía.", "UtilLock reads the app name on screen and, in supported browsers, the address-bar text. This is used only to apply your rules and is not stored or sent.")
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    disclosure = null
                    if (isVpn) {
                        val permission = VpnController.permissionIntent(context)
                        if (permission == null) {
                            VpnController.start(context)
                            repository.setDnsVpn(true)
                        } else vpnLauncher.launch(permission)
                    } else context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text(tr("Entiendo y continuar", "I understand, continue")) }
            },
            dismissButton = { OutlinedButton(onClick = { disclosure = null }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

@Composable
private fun ProfileScreen(
    premium: Boolean,
    repository: ProtectionRepository,
    sessions: SessionRepository,
    backend: BackendClient,
    billing: BillingRepository,
    activity: Activity,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by billing.state.collectAsState()
    val linked by sessions.linked.collectAsState()
    val language = currentLanguage()
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val setupAccountMessage = tr(
        "Configura Supabase y habilita Google + vinculación manual.",
        "Configure Supabase and enable Google plus manual linking.",
    )
    LaunchedEffect(linked) { if (linked) billing.restore() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowIconBadge(Icons.Rounded.Person, size = 46.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(tr("Perfil", "Profile"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (linked) tr("Cuenta de Google vinculada. Tus compras se pueden restaurar.", "Google account linked. Your purchases can be restored.")
                else tr("Empiezas con una cuenta anónima. Vincula Google antes de suscribirte.", "You start with an anonymous account. Link Google before subscribing."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!linked) {
                OutlinedButton(
                    enabled = sessions.isConfigured(),
                    onClick = {
                        scope.launch {
                            val url = sessions.googleLinkUrl()
                            if (url == null) accountMessage = setupAccountMessage
                            else context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(tr("Vincular cuenta de Google", "Link Google account")) }
                accountMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(if (premium) UtilLockGradients.premium else UtilLockGradients.hero)
                    .padding(22.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Workspaces, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (premium) "UtilLock Premium" else tr("Plan gratuito", "Free plan"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (premium) tr("Hasta 30 evaluaciones con IA por día.", "Up to 30 AI evaluations per day.") else tr("Incluye 4 pausas verificadas con IA; luego, reto local con espera.", "Includes 4 AI-verified pauses, then a local challenge with a wait."),
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(16.dp))
                    if (!premium) {
                        PremiumButton(
                            text = state.product?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice?.let { tr("Suscribirme · $it/mes", "Subscribe · $it/month") }
                                ?: tr("Cargando precio…", "Loading price…"),
                            onClick = { billing.launchPurchase(activity) },
                            enabled = linked && state.product != null,
                            modifier = Modifier.fillMaxWidth(),
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0.9f))),
                            contentColor = app.utillock.android.ui.theme.Violet700,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        tr("Restaurar compra", "Restore purchase"),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(enabled = linked, onClick = billing::restore),
                    )
                    state.message?.let { Text(it, color = Color.White) }
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))
            Text(tr("Idioma", "Language"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("es" to "Español", "en" to "English").forEach { (code, label) ->
                    val selected = language == code
                    OutlinedButton(
                        onClick = { setLanguage(context, code) },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (selected) "✓ $label" else label) }
                }
            }
        }
        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))
            Text(tr("Privacidad", "Privacy"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                tr(
                    "Las fotos no se conservan después de la evaluación. Las evaluaciones en línea usan store=false; la alternativa OCR permanece en el dispositivo.",
                    "Photos are not retained after evaluation. Online evaluations use store=false; fallback OCR stays on the device.",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = sessions.isConfigured(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp),
                shape = MaterialTheme.shapes.medium,
            ) { Text(tr("Eliminar cuenta y datos", "Delete account and data"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        val failedMessage = tr("No se pudo eliminar la cuenta. Inténtalo de nuevo.", "The account could not be deleted. Try again.")
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Eliminar permanentemente", "Delete permanently"), fontWeight = FontWeight.Bold) },
            text = { Text(tr("Se borrarán tu cuenta, cuota, historial de retos y vínculo de compra en UtilLock. La suscripción de Play debe cancelarse aparte.", "Your UtilLock account, quota, challenge history, and purchase link will be deleted. Cancel the Play subscription separately.")) },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (backend.deleteAccount()) {
                            sessions.clear()
                            repository.reset()
                            accountMessage = null
                        } else accountMessage = failedMessage
                    }
                }) { Text(tr("Sí, eliminar", "Yes, delete")) }
            },
            dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text(tr("Cancelar", "Cancel")) } },
        )
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val component = ComponentName(context, app.utillock.android.blocking.AppBlockAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { it.equals(component, ignoreCase = true) }
}

private fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    return mode == AppOpsManager.MODE_ALLOWED
}

private fun formatMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
