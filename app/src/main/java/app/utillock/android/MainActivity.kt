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
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SettingsAccessibility
import androidx.compose.material.icons.rounded.SavedSearch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
import app.utillock.android.model.InstalledApp
import app.utillock.android.model.ScheduleEvaluator
import app.utillock.android.ui.components.CountdownRing
import app.utillock.android.ui.components.EmptyState
import app.utillock.android.ui.components.FeatureRow
import app.utillock.android.ui.components.GlowIconBadge
import app.utillock.android.ui.components.GradientCard
import app.utillock.android.ui.components.PremiumButton
import app.utillock.android.ui.components.SectionHeader
import app.utillock.android.ui.theme.UtilLockGradients
import app.utillock.android.ui.theme.UtilLockTheme
import app.utillock.android.ui.tr
import app.utillock.android.ui.currentLanguage
import app.utillock.android.ui.setLanguage
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as UtilLockApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { container.sessionRepository.importAuthRedirect(intent.data) }
        container.billingRepository.connect()
        lifecycleScope.launch {
            if (container.protectionRepository.awaitLoaded().usageMonitorEnabled && hasUsageAccess(this@MainActivity)) {
                UsageMonitorService.start(this@MainActivity)
            }
        }
        setContent {
            UtilLockTheme {
                UtilLockApp(
                    container.protectionRepository,
                    container.sessionRepository,
                    container.backendClient,
                    container.billingRepository,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { container.sessionRepository.importAuthRedirect(intent.data) }
    }
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

@Composable
private fun DashboardScreen(repository: ProtectionRepository, now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val active = ScheduleEvaluator.activeProtection(state, LocalDateTime.now(), now)
    var duration by remember { mutableIntStateOf(60) }
    var showApps by remember { mutableStateOf(false) }
    var showSites by remember { mutableStateOf(false) }
    var showSchedule by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showQuickBlockSetup by remember { mutableStateOf(false) }
    // A setup is considered ready only when the user has selected at least one destination.
    val hasBlockingConfiguration = state.blockedPackages.isNotEmpty() || state.blockedDomains.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowIconBadge(Icons.Rounded.Security, size = 40.dp)
                Spacer(Modifier.size(10.dp))
                Text("UtilLock", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
        item {
            HeroCard(
                state = state,
                active = active,
                now = now,
                duration = duration,
                repository = repository,
                context = context,
                hasBlockingConfiguration = hasBlockingConfiguration,
                onOpenSetup = { showQuickBlockSetup = true },
                onNeedsPermission = { showPermissionDialog = true },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(30 to "30 min", 60 to "60 min", 120 to "2 h").forEach { (minutes, label) ->
                    val chosen = duration == minutes
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { duration = minutes }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ShortcutTile(
                    icon = Icons.Rounded.AppBlocking,
                    label = tr("Apps", "Apps"),
                    caption = "${state.blockedPackages.size}",
                    modifier = Modifier.weight(1f),
                    onClick = { showApps = true },
                )
                ShortcutTile(
                    icon = Icons.Rounded.Language,
                    label = tr("Sitios", "Sites"),
                    caption = "${state.blockedDomains.size}",
                    modifier = Modifier.weight(1f),
                    onClick = { showSites = true },
                )
            }
        }
        item {
            SectionHeader(
                title = tr("Programaciones", "Schedules"),
                subtitle = tr("Automatiza tu bloqueo cada semana", "Automate blocking every week"),
                trailing = {
                    GlowIconBadge(
                        icon = Icons.Rounded.Add,
                        size = 40.dp,
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

    if (showApps) AppPickerDialog(repository, onDismiss = { showApps = false })
    if (showSites) SitePickerDialog(repository, onDismiss = { showSites = false })
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
private fun HeroCard(
    state: app.utillock.android.model.ProtectionState,
    active: app.utillock.android.model.ActiveProtection,
    now: Long,
    duration: Int,
    repository: ProtectionRepository,
    context: Context,
    hasBlockingConfiguration: Boolean,
    onOpenSetup: () -> Unit,
    onNeedsPermission: () -> Unit,
) {
    val quickActive = state.isQuickBlockActive(now)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(UtilLockGradients.hero)
            .padding(24.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Bloqueo rápido", "Quick block"),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                StatusPillOnGradient(
                    active = active.active,
                    blockedApps = state.blockedPackages.size,
                    blockedSites = state.blockedDomains.size,
                )
            }
            Spacer(Modifier.height(20.dp))
            if (quickActive) {
                val remainingMs = (state.quickBlockUntilEpochMs - now).coerceAtLeast(0)
                val remainingSeconds = remainingMs / 1000
                val totalSeconds = (duration * 60).coerceAtLeast(1)
                val fraction = (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountdownRing(
                        progress = fraction,
                        diameter = 96.dp,
                        strokeWidth = 8.dp,
                        center = {
                            Text(
                                formatCountdown(remainingSeconds),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                    )
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tr("Bloqueo en curso", "Block in progress"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            tr("Vuelve más tarde o detén el bloqueo si es urgente.", "Come back later, or stop the block if it's urgent."),
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                PremiumButton(
                    text = tr("Detener bloqueo", "Stop block"),
                    onClick = repository::stopQuickBlock,
                    modifier = Modifier.fillMaxWidth(),
                    brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.14f))),
                )
            } else {
                // Preview-only UI: keep the CTA enabled while the real setup flow is still pending.
                val canStart = true
                val startBlock = {
                    if (!hasBlockingConfiguration) {
                        onOpenSetup()
                    } else {
                        val accessibilityReady = isAccessibilityEnabled(context)
                        val usageReady = state.usageMonitorEnabled && hasUsageAccess(context)
                        if (accessibilityReady || usageReady) {
                            repository.setQuickBlock(duration)
                        } else {
                            onNeedsPermission()
                        }
                    }
                }

                Text(
                    tr("Recupera El Control Ya!!!!", "Take Back Control Now!!!!"),
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF3395F4).copy(alpha = if (canStart) 1f else 0.45f))
                        .clickable(enabled = canStart, onClick = startBlock),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = tr("Iniciar bloqueo", "Start block"),
                            tint = Color.White,
                            modifier = Modifier.size(42.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr("Iniciar", "Start"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DemoBlockMode(
                        icon = Icons.Rounded.LockClock,
                        label = tr("Temporizado", "Timed"),
                        onClick = onOpenSetup,
                        modifier = Modifier.weight(1f),
                    )
                    DemoBlockMode(
                        icon = Icons.Rounded.Bolt,
                        label = "Pomodoro",
                        onClick = onOpenSetup,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoBlockMode(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(66.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
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
private fun ShortcutTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, caption: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlowIconBadge(icon, size = 38.dp, brush = UtilLockGradients.heroSoft)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, fontWeight = FontWeight.Bold)
                Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
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
private fun AppPickerDialog(repository: ProtectionRepository, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    var apps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = loadLaunchableApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Aplicaciones a bloquear", "Apps to block"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(tr("Buscar", "Search")) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                )
                LazyColumn(Modifier.height(420.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        val selected = app.packageName in state.blockedPackages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { repository.togglePackage(app.packageName) }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = selected, onCheckedChange = { repository.togglePackage(app.packageName) })
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(tr("Listo", "Done")) } },
    )
}

@Composable
private fun SitePickerDialog(repository: ProtectionRepository, onDismiss: () -> Unit) {
    val state by repository.state.collectAsState()
    var domain by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Protección de sitios", "Website protection"), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Contenido para adultos", "Adult content"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Usa DNS familiar y reglas del navegador", "Uses family DNS and browser rules"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.adultFilterEnabled, onCheckedChange = repository::setAdultFilter)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(domain, { domain = it }, label = { Text("ejemplo.com") }, singleLine = true, shape = MaterialTheme.shapes.medium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { repository.addDomain(domain); domain = "" }, enabled = domain.isNotBlank()) { Text(tr("Añadir dominio", "Add domain")) }
                Spacer(Modifier.height(10.dp))
                state.blockedDomains.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(item, modifier = Modifier.weight(1f))
                        Text(tr("Quitar", "Remove"), color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { repository.removeDomain(item) }.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text(tr("Listo", "Done")) } },
    )
}

@Composable
private fun ScheduleDialog(repository: ProtectionRepository, onDismiss: () -> Unit) {
    val state by repository.state.collectAsState()
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("17:00") }
    var adult by remember { mutableStateOf(state.adultFilterEnabled) }
    var days by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    val valid = parseMinute(start) != null && parseMinute(end) != null && name.isNotBlank() && days.isNotEmpty()
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
                    repository.addSchedule(
                        BlockSchedule(
                            name = name.trim(),
                            startMinute = parseMinute(start)!!,
                            endMinute = parseMinute(end)!!,
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

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 24.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(tr("Protección", "Protection"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(tr("Android exige que actives cada capacidad de forma explícita.", "Android requires you to enable each capability explicitly."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
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
                            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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

private suspend fun loadLaunchableApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val results = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION") context.packageManager.queryIntentActivities(intent, 0)
    }
    results.mapNotNull { info ->
        val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
        if (packageName == context.packageName) return@mapNotNull null
        InstalledApp(packageName, info.loadLabel(context.packageManager).toString())
    }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
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

private fun parseMinute(value: String): Int? {
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun formatMinute(value: Int): String = "%02d:%02d".format(value / 60, value % 60)
