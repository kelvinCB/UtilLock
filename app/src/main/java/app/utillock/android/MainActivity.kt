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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AppBlocking
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LockClock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
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
import app.utillock.android.ui.theme.UtilLockTheme
import app.utillock.android.ui.tr
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as UtilLockApplication).container }

    override fun onCreate(savedInstanceState: Bundle?) {
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

private enum class AppTab(val es: String, val en: String) {
    BLOCK("Bloqueo", "Block"),
    PROTECTION("Protección", "Protection"),
    PROFILE("Perfil", "Profile"),
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
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            Icon(
                                when (item) {
                                    AppTab.BLOCK -> Icons.Rounded.Shield
                                    AppTab.PROTECTION -> Icons.Rounded.Settings
                                    AppTab.PROFILE -> Icons.Rounded.Person
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(tr(item.es, item.en)) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            AppTab.BLOCK -> DashboardScreen(repository, now, Modifier.padding(padding))
            AppTab.PROTECTION -> ProtectionScreen(repository, Modifier.padding(padding))
            AppTab.PROFILE -> ProfileScreen(
                premium = state.premium,
                repository = repository,
                sessions = sessions,
                backend = backend,
                billing = billing,
                activity = activity,
                modifier = Modifier.padding(padding),
            )
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("  UtilLock", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (active.active) tr("ACTIVO", "ACTIVE") else tr("LISTO", "READY"),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(tr("Bloqueo rápido", "Quick block"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.blockedPackages.size} apps · ${state.blockedDomains.size} ${tr("sitios", "sites")} · ${if (state.adultFilterEnabled) tr("+18 activo", "adult filter on") else tr("+18 inactivo", "adult filter off")}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60, 120).forEach { minutes ->
                            AssistChip(
                                onClick = { duration = minutes },
                                label = { Text(if (minutes == 120) "2 h${if (duration == minutes) " ✓" else ""}" else "$minutes min${if (duration == minutes) " ✓" else ""}") },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (state.isQuickBlockActive(now)) {
                        Button(onClick = repository::stopQuickBlock, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(tr("Detener bloqueo rápido", "Stop quick block"))
                        }
                    } else {
                        Button(
                            onClick = {
                                val accessibilityReady = isAccessibilityEnabled(context)
                                val usageReady = state.usageMonitorEnabled && hasUsageAccess(context)
                                if (accessibilityReady || usageReady) {
                                    repository.setQuickBlock(duration)
                                } else {
                                    showPermissionDialog = true
                                }
                            },
                            enabled = state.blockedPackages.isNotEmpty() || state.blockedDomains.isNotEmpty() || state.adultFilterEnabled,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Text(tr("  Iniciar", "  Start"))
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showApps = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.AppBlocking, contentDescription = null)
                    Text(tr(" Apps", " Apps"))
                }
                OutlinedButton(onClick = { showSites = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Language, contentDescription = null)
                    Text(tr(" Sitios", " Sites"))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Programaciones", "Schedules"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                FloatingActionButton(onClick = { showSchedule = true }) { Icon(Icons.Rounded.Add, contentDescription = null) }
            }
        }
        if (state.schedules.isEmpty()) {
            item {
                Text(
                    tr("Aún no hay horarios. Añade uno para automatizar el bloqueo.", "No schedules yet. Add one to automate blocking."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun ScheduleCard(schedule: BlockSchedule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LockClock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("  ${schedule.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = schedule.enabled, onCheckedChange = onToggle)
            }
            Text(
                "${formatMinute(schedule.startMinute)}–${formatMinute(schedule.endMinute)} · ${schedule.days.size} días",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("${schedule.packages.size} apps · ${schedule.domains.size} ${tr("sitios", "sites")}${if (schedule.blockAdultContent) " · +18" else ""}")
            OutlinedButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) { Text(tr("Eliminar", "Delete")) }
        }
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
        title = { Text(tr("Aplicaciones a bloquear", "Apps to block")) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text(tr("Buscar", "Search")) }, singleLine = true)
                LazyColumn(Modifier.height(420.dp)) {
                    items(apps.filter { it.label.contains(query, true) }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { repository.togglePackage(app.packageName) }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = app.packageName in state.blockedPackages, onCheckedChange = { repository.togglePackage(app.packageName) })
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
        title = { Text(tr("Protección de sitios", "Website protection")) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Contenido para adultos", "Adult content"), fontWeight = FontWeight.SemiBold)
                        Text(tr("Usa DNS familiar y reglas del navegador", "Uses family DNS and browser rules"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.adultFilterEnabled, onCheckedChange = repository::setAdultFilter)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(domain, { domain = it }, label = { Text("ejemplo.com") }, singleLine = true)
                Button(onClick = { repository.addDomain(domain); domain = "" }, enabled = domain.isNotBlank()) { Text(tr("Añadir dominio", "Add domain")) }
                Spacer(Modifier.height(10.dp))
                state.blockedDomains.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
        title = { Text(tr("Nueva programación", "New schedule")) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Nombre", "Name")) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text(tr("Inicio", "Start")) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text(tr("Fin", "End")) }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tr("L,M,X,J,V,S,D", "M,T,W,T,F,S,S").split(',').forEachIndexed { index, label ->
                        FilterChip(
                            selected = index + 1 in days,
                            onClick = { days = if (index + 1 in days) days - (index + 1) else days + (index + 1) },
                            label = { Text(label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Bloquear contenido +18", "Block adult content"), modifier = Modifier.weight(1f))
                    Switch(checked = adult, onCheckedChange = { adult = it })
                }
                Text(tr("Usará las apps y sitios seleccionados actualmente.", "Uses the currently selected apps and sites."), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(tr("Protección", "Protection"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(tr("Android exige que actives cada capacidad de forma explícita.", "Android requires you to enable each capability explicitly."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            PermissionCard(
                title = tr("Accesibilidad", "Accessibility"),
                description = tr("Detecta las apps elegidas y la URL visible en navegadores compatibles.", "Detects selected apps and the visible URL in supported browsers."),
                enabled = accessibility,
                onClick = { disclosure = "accessibility" },
            )
        }
        item {
            PermissionCard(
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
            PermissionCard(
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
                PermissionCard(
                    title = tr("Notificaciones", "Notifications"),
                    description = tr("Necesaria para mostrar que el filtro DNS permanece activo.", "Required to show that DNS filtering remains active."),
                    enabled = false,
                    onClick = { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
        }
        item {
            OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text(tr("Actualizar estado", "Refresh status")) }
        }
    }

    if (disclosure != null) {
        val isVpn = disclosure == "vpn"
        AlertDialog(
            onDismissRequest = { disclosure = null },
            title = { Text(if (isVpn) tr("Antes de activar la VPN local", "Before enabling the local VPN") else tr("Antes de activar Accesibilidad", "Before enabling Accessibility")) },
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
private fun PermissionCard(title: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (enabled) Icons.Rounded.CheckCircle else Icons.Rounded.Settings,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (enabled) "OK" else tr("Activar", "Enable"), color = MaterialTheme.colorScheme.primary)
        }
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
    var accountMessage by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val setupAccountMessage = tr(
        "Configura Supabase y habilita Google + vinculación manual.",
        "Configure Supabase and enable Google plus manual linking.",
    )
    LaunchedEffect(linked) { if (linked) billing.restore() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(tr("Perfil", "Profile"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
                ) { Text(tr("Vincular cuenta de Google", "Link Google account")) }
                accountMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text(if (premium) "UtilLock Premium" else tr("Plan gratuito", "Free plan"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (premium) tr("Hasta 30 evaluaciones con IA por día.", "Up to 30 AI evaluations per day.") else tr("Incluye 4 pausas verificadas con IA; luego, reto local con espera.", "Includes 4 AI-verified pauses, then a local challenge with a wait."))
                    Spacer(Modifier.height(12.dp))
                    if (!premium) {
                        Button(onClick = { billing.launchPurchase(activity) }, enabled = linked && state.product != null) {
                            Text(state.product?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice?.let { tr("Suscribirme · $it/mes", "Subscribe · $it/month") } ?: tr("Cargando precio…", "Loading price…"))
                        }
                    }
                    OutlinedButton(onClick = billing::restore, enabled = linked) { Text(tr("Restaurar compra", "Restore purchase")) }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item {
            HorizontalDivider()
            Text(tr("Privacidad", "Privacy"), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(
                tr(
                    "Las fotos no se conservan después de la evaluación. Las evaluaciones en línea usan store=false; la alternativa OCR permanece en el dispositivo.",
                    "Photos are not retained after evaluation. Online evaluations use store=false; fallback OCR stays on the device.",
                ),
            )
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = sessions.isConfigured(),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text(tr("Eliminar cuenta y datos", "Delete account and data"), color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        val failedMessage = tr("No se pudo eliminar la cuenta. Inténtalo de nuevo.", "The account could not be deleted. Try again.")
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Eliminar permanentemente", "Delete permanently")) },
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
